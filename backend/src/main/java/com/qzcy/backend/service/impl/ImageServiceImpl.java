package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.ImageEstimateDto;
import com.qzcy.backend.entity.ImageGenerationConfig;
import com.qzcy.backend.entity.ImageGenerationMetric;
import com.qzcy.backend.entity.ImageRecord;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.ImageGenerationMetricMapper;
import com.qzcy.backend.mapper.ImageRecordMapper;
import com.qzcy.backend.service.ImageGenerationConfigService;
import com.qzcy.backend.service.ImageService;
import com.qzcy.backend.service.PaymentService;
import com.qzcy.backend.util.UploadPathUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executor;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class ImageServiceImpl implements ImageService {
    private final ImageRecordMapper imageRecordMapper;
    private final ImageGenerationMetricMapper metricMapper;
    private final PaymentService paymentService;
    private final ImageGenerationConfigService configService;
    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper;
    @Qualifier("imageGenerationExecutor")
    private final Executor imageGenerationExecutor;

    @Value("${app.upload.image-path:userImage/}")
    private String imagePath;

    @Value("${server.port:8080}")
    private int serverPort;

    private static final Duration IMAGE_TIMEOUT = Duration.ofMinutes(10);
    private static final int MAX_LOG_BODY_CHARS = 4000;
    private static final int MAX_REFERENCE_IMAGES = 4;
    private static final int MAX_REFERENCE_IMAGE_CHARS = 12 * 1024 * 1024;

    public ImageServiceImpl(ImageRecordMapper imageRecordMapper,
                            ImageGenerationMetricMapper metricMapper,
                            PaymentService paymentService,
                            ImageGenerationConfigService configService,
                            RestTemplateBuilder restTemplateBuilder,
                            ObjectMapper objectMapper,
                            @Qualifier("imageGenerationExecutor") Executor imageGenerationExecutor) {
        this.imageRecordMapper = imageRecordMapper;
        this.metricMapper = metricMapper;
        this.paymentService = paymentService;
        this.configService = configService;
        this.restTemplateBuilder = restTemplateBuilder;
        this.objectMapper = objectMapper;
        this.imageGenerationExecutor = imageGenerationExecutor;
    }

    @Override
    public ImageRecord submit(Long userId, String username, String prompt, String qualityCode, String size, List<String> referenceImages) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new BusinessException(400, "提示词不能为空");
        }

        ImageGenerationConfig config = configService.requireEnabled(qualityCode);
        String resolvedSize = normalizeRequestedSize(size, config.getSize());
        List<String> normalizedReferenceImages = normalizeReferenceImages(referenceImages);
        ImageRecord record = new ImageRecord();
        record.setUserId(userId);
        record.setPrompt(prompt.trim());
        record.setStatus("pending");
        record.setGenerationModel(config.getModel());
        record.setCost(config.getPrice());
        record.setCreatedAt(LocalDateTime.now());
        imageRecordMapper.insert(record);

        boolean balanceDeducted = false;
        try {
            log.info("图像生成任务提交，recordId={}, userId={}, configCode={}, model={}, size={}, quality={}",
                    record.getId(), userId, config.getCode(), config.getModel(), resolvedSize, config.getQuality());
            paymentService.deductBalance(userId, config.getPrice());
            balanceDeducted = true;
            imageGenerationExecutor.execute(() -> runGeneration(record.getId(), username, prompt.trim(), config, resolvedSize, normalizedReferenceImages));
            return record;
        } catch (BusinessException ex) {
            record.setStatus("failed");
            imageRecordMapper.updateById(record);
            throw ex;
        } catch (Exception ex) {
            record.setStatus("failed");
            imageRecordMapper.updateById(record);
            if (balanceDeducted) {
                refundFailedGeneration(record);
            }
            throw new BusinessException(500, "图像任务提交失败：" + ex.getMessage());
        }
    }

    @Override
    public ImageRecord detail(Long userId, Long imageRecordId) {
        ImageRecord record = imageRecordMapper.selectById(imageRecordId);
        if (record == null) {
            throw new BusinessException(404, "图像记录不存在");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(403, "只能查看自己的图像记录");
        }
        return record;
    }

    @Override
    public Page<ImageRecord> history(Long userId, long page, long size) {
        return imageRecordMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<ImageRecord>()
                        .eq(ImageRecord::getUserId, userId)
                        .orderByDesc(ImageRecord::getCreatedAt)
        );
    }

    @Override
    public ImageEstimateDto estimate() {
        Long averageDurationMs = metricMapper.averageDurationMs();
        Integer sampleCount = Math.toIntExact(metricMapper.selectCount(null));
        long fallbackMs = Duration.ofSeconds(45).toMillis();
        return new ImageEstimateDto(averageDurationMs == null || averageDurationMs <= 0 ? fallbackMs : averageDurationMs, sampleCount);
    }

    @Override
    public void delete(Long userId, Long imageRecordId) {
        ImageRecord record = imageRecordMapper.selectById(imageRecordId);
        if (record == null) {
            throw new BusinessException(404, "图像记录不存在");
        }
        if (!userId.equals(record.getUserId())) {
            throw new BusinessException(403, "只能删除自己的图像记录");
        }
        deleteLocalFile(record.getGeneratedImageUrl());
        imageRecordMapper.deleteById(imageRecordId);
    }

    private void runGeneration(Long recordId, String username, String prompt, ImageGenerationConfig config, String size, List<String> referenceImages) {
        long startedAt = System.currentTimeMillis();
        log.info("图像生成任务开始执行，recordId={}, thread={}", recordId, Thread.currentThread().getName());
        ImageRecord record = imageRecordMapper.selectById(recordId);
        if (record == null) {
            log.warn("图像生成任务记录不存在，recordId={}", recordId);
            return;
        }

        try {
            String relativePath = requestAndSaveImage(recordId, username, prompt, config, size, referenceImages);
            record.setGeneratedImageUrl(relativePath);
            record.setStatus("success");
            record.setErrorStatusCode(null);
            record.setErrorType("");
            record.setErrorMessage("");
            imageRecordMapper.updateById(record);
            saveMetric(record.getId(), record.getUserId(), config.getCode(), System.currentTimeMillis() - startedAt);
        } catch (Exception ex) {
            log.warn("图像生成任务失败，recordId={}", recordId, ex);
            ImageRecord failedRecord = imageRecordMapper.selectById(recordId);
            if (failedRecord != null) {
                failedRecord.setStatus("failed");
                if (isBlank(failedRecord.getErrorType())) {
                    failedRecord.setErrorType(errorType(ex));
                }
                if (isBlank(failedRecord.getErrorMessage())) {
                    failedRecord.setErrorMessage(truncateErrorMessage(ex.getMessage()));
                }
                imageRecordMapper.updateById(failedRecord);
                refundFailedGeneration(failedRecord);
            }
        }
    }

    private void refundFailedGeneration(ImageRecord record) {
        try {
            paymentService.refundBalance(record.getUserId(), record.getCost());
            log.info("图像生成失败已退还余额，recordId={}, userId={}, amount={}",
                    record.getId(), record.getUserId(), record.getCost());
        } catch (Exception refundEx) {
            log.warn("图像生成失败余额退还失败，recordId={}, userId={}, amount={}",
                    record.getId(), record.getUserId(), record.getCost(), refundEx);
        }
    }

    private String requestAndSaveImage(Long recordId, String username, String prompt, ImageGenerationConfig config, String size, List<String> referenceImages) throws Exception {
        boolean hasReferenceImages = referenceImages != null && !referenceImages.isEmpty();
        String effectiveQuality = effectiveQuality(config);
        HashMap<String, Object> body = generationImageBody(prompt, config, size, referenceImages);

        if (effectiveQuality != null) {
            body.put("quality", effectiveQuality);
        }

        String requestUrl = imageApiUrl(config, hasReferenceImages ? "/v1/images/edits" : "/v1/images/generations");
        persistImageRequestInfo(recordId, config.getModel(), requestUrl);
        long requestStartedAt = System.currentTimeMillis();
        log.info("Image generation request payload metadata, recordId={}, referenceImages={}, referenceImageField={}",
                recordId, hasReferenceImages ? referenceImages.size() : 0, referenceImageField(referenceImages));
        log.info("开始请求OpenAI图像接口，url={}, model={}, size={}, quality={}, effectiveQuality={}, referenceImages={}",
                requestUrl, config.getModel(), size, config.getQuality(), effectiveQuality, hasReferenceImages ? referenceImages.size() : 0);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = hasReferenceImages
                ? multipartImageRequest(requestUrl, config.getApiKey(), body, referenceImages)
                : HttpRequest.newBuilder(URI.create(requestUrl))
                .timeout(IMAGE_TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        log.info("OpenAI图像接口收到响应头，url={}, status={}, durationMs={}",
                requestUrl, response.statusCode(), System.currentTimeMillis() - requestStartedAt);

        String responseBody;
        try (InputStream responseStream = response.body()) {
            responseBody = readJsonBodyUntilComplete(responseStream, requestStartedAt, requestUrl);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("OpenAI图像接口返回错误，url={}, status={}, body={}",
                    requestUrl, response.statusCode(), truncateForLog(responseBody));
            if (response.statusCode() == 524) {
                persistImageError(recordId, response.statusCode(), "gateway_timeout", requestUrl, responseBody);
                throw new IllegalStateException("中转服务网关超时：同步生图请求在服务商网关层超时，请将 gpt-image 系列质量改为 auto/medium/high，或更换支持长连接/异步任务的中转线路");
            }
            persistImageError(recordId, response.statusCode(), responseErrorType(response.statusCode(), responseBody), requestUrl, responseBody);
            throw new IllegalStateException("OpenAI接口返回错误：" + response.statusCode() + " " + responseBody);
        }

        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode first = json.path("data").path(0);
        if (first != null && !first.isMissingNode() && first.hasNonNull("b64_json")) {
            return saveBase64Image(username, first.get("b64_json").asText());
        }
        if (first != null && !first.isMissingNode() && first.hasNonNull("url")) {
            return saveRemoteImage(username, first.get("url").asText());
        }
        String imageData = recursiveText(json, "b64_json");
        if (!isBlank(imageData)) {
            return saveBase64Image(username, imageData);
        }
        String imageUrl = recursiveText(json, "url");
        if (isBlank(imageUrl)) {
            imageUrl = recursiveText(json, "image_url");
        }
        if (!isBlank(imageUrl)) {
            return saveRemoteImage(username, imageUrl);
        }
        throw new IllegalStateException("OpenAI接口返回中没有 url 或 b64_json");
    }

    private HashMap<String, Object> generationImageBody(String prompt, ImageGenerationConfig config, String size, List<String> referenceImages) {
        HashMap<String, Object> body = new HashMap<>();
        body.put("model", config.getModel());
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", size);
        body.put("stream", false);
        return body;
    }

    private String referenceImageField(List<String> referenceImages) {
        if (referenceImages == null || referenceImages.isEmpty()) {
            return "none";
        }
        return referenceImages.size() == 1 ? "edits:multipart:image" : "edits:multipart:image[]";
    }

    private HttpRequest multipartImageRequest(String requestUrl, String apiKey, HashMap<String, Object> fields, List<String> referenceImages) throws Exception {
        String boundary = "----imageCreaterBoundary" + UUID.randomUUID();
        byte[] body = imageMultipartBody(fields, referenceImages, boundary);
        return HttpRequest.newBuilder(URI.create(requestUrl))
                .timeout(IMAGE_TIMEOUT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
    }

    private byte[] imageMultipartBody(HashMap<String, Object> fields, List<String> referenceImages, String boundary) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (var entry : fields.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"" + escapeMultipartName(entry.getKey()) + "\"\r\n\r\n");
            output.write(String.valueOf(entry.getValue()).getBytes(StandardCharsets.UTF_8));
            writeAscii(output, "\r\n");
        }
        for (int index = 0; index < referenceImages.size(); index++) {
            ReferenceImage image = parseReferenceImage(referenceImages.get(index));
            String fieldName = referenceImages.size() == 1 ? "image" : "image[]";
            writeAscii(output, "--" + boundary + "\r\n");
            writeAscii(output, "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"reference-" + (index + 1) + image.extension() + "\"\r\n");
            writeAscii(output, "Content-Type: " + image.mimeType() + "\r\n\r\n");
            output.write(image.bytes());
            writeAscii(output, "\r\n");
        }
        writeAscii(output, "--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private ReferenceImage parseReferenceImage(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (comma <= 0) {
            throw new BusinessException(400, "参考图 data URL 格式错误");
        }
        String meta = dataUrl.substring(0, comma);
        String base64 = dataUrl.substring(comma + 1);
        String mimeType = meta.substring("data:".length());
        int semi = mimeType.indexOf(';');
        if (semi >= 0) {
            mimeType = mimeType.substring(0, semi);
        }
        if (mimeType.isBlank() || !mimeType.startsWith("image/")) {
            mimeType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return new ReferenceImage(mimeType, Base64.getDecoder().decode(base64));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(400, "参考图 base64 解码失败");
        }
    }

    private void writeAscii(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private String escapeMultipartName(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ReferenceImage(String mimeType, byte[] bytes) {
        String extension() {
            return switch (mimeType.toLowerCase()) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/webp" -> ".webp";
                case "image/gif" -> ".gif";
                default -> ".png";
            };
        }
    }

    private String recursiveText(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.hasNonNull(fieldName)) {
            return node.get(fieldName).asText();
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                String found = recursiveText(fields.next().getValue(), fieldName);
                if (!isBlank(found)) {
                    return found;
                }
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String found = recursiveText(child, fieldName);
                if (!isBlank(found)) {
                    return found;
                }
            }
        }
        return null;
    }

    private List<String> normalizeReferenceImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String image : images) {
            if (isBlank(image)) {
                continue;
            }
            String value = image.trim();
            if (!value.startsWith("data:image/")) {
                throw new BusinessException(400, "参考图格式必须是图片 data URL");
            }
            if (value.length() > MAX_REFERENCE_IMAGE_CHARS) {
                throw new BusinessException(400, "单张参考图过大，请压缩后上传");
            }
            normalized.add(value);
            if (normalized.size() > MAX_REFERENCE_IMAGES) {
                throw new BusinessException(400, "最多上传 " + MAX_REFERENCE_IMAGES + " 张参考图");
            }
        }
        return List.copyOf(normalized);
    }

    private String effectiveQuality(ImageGenerationConfig config) {
        String model = config.getModel() == null ? "" : config.getModel().trim().toLowerCase();
        String quality = config.getQuality() == null ? "" : config.getQuality().trim().toLowerCase();
        if (model.startsWith("gpt-image")) {
            if (quality.isBlank() || "standard".equals(quality)) {
                return "auto";
            }
            if ("hd".equals(quality)) {
                return "high";
            }
            if ("auto".equals(quality) || "low".equals(quality) || "medium".equals(quality) || "high".equals(quality)) {
                return quality;
            }
            return "auto";
        }
        return quality.isBlank() ? null : quality;
    }

    private String normalizeRequestedSize(String requestedSize, String fallbackSize) {
        String fallback = fallbackSize == null || fallbackSize.isBlank() ? "1024x1024" : fallbackSize.trim();
        if (requestedSize == null || requestedSize.isBlank()) {
            return fallback;
        }
        String size = requestedSize.trim().toLowerCase();
        if ("auto".equals(size)) {
            return size;
        }
        if (!size.matches("^\\d{3,5}x\\d{3,5}$")) {
            throw new BusinessException(400, "尺寸格式应为 auto 或 1024x1024");
        }
        String[] parts = size.split("x");
        int width = Integer.parseInt(parts[0]);
        int height = Integer.parseInt(parts[1]);
        if (width < 256 || height < 256 || width > 8192 || height > 8192) {
            throw new BusinessException(400, "尺寸范围应在 256 到 8192 像素之间");
        }
        return width + "x" + height;
    }

    private String readJsonBodyUntilComplete(InputStream inputStream, long requestStartedAt, String requestUrl) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            String text = buffer.toString(StandardCharsets.UTF_8);
            if (isCompleteJson(text)) {
                log.info("OpenAI图像接口响应体读取完成，url={}, durationMs={}, bodyChars={}",
                        requestUrl, System.currentTimeMillis() - requestStartedAt, text.length());
                return text;
            }
            if (buffer.size() > 30 * 1024 * 1024) {
                throw new IllegalStateException("OpenAI接口响应体过大，已超过30MB");
            }
        }
        String text = buffer.toString(StandardCharsets.UTF_8);
        log.info("OpenAI图像接口连接关闭，url={}, durationMs={}, bodyChars={}",
                requestUrl, System.currentTimeMillis() - requestStartedAt, text.length());
        return text;
    }

    private boolean isCompleteJson(String text) {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = inString;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth += 1;
            } else if (ch == '}') {
                depth -= 1;
                if (depth == 0) {
                    return trimmed.substring(i + 1).trim().isEmpty();
                }
            }
        }
        return false;
    }

    private String imageApiUrl(ImageGenerationConfig config) {
        return imageApiUrl(config, null);
    }

    private String imageApiUrl(ImageGenerationConfig config, String forcedPath) {
        String baseUrl = config.getApiBaseUrl() == null || config.getApiBaseUrl().isBlank()
                ? "https://api.openai.com"
                : config.getApiBaseUrl().trim();
        baseUrl = normalizeLocalRelayBaseUrl(baseUrl);
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String path = forcedPath != null && !forcedPath.isBlank()
                ? forcedPath
                : config.getEndpointPath() == null || config.getEndpointPath().isBlank()
                ? "/v1/images/generations"
                : config.getEndpointPath().trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (baseUrl.endsWith("/v1") && path.startsWith("/v1/")) {
            path = path.substring(3);
        }
        return baseUrl + path;
    }

    private String normalizeLocalRelayBaseUrl(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();
            boolean localHost = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
            if (localHost && port == 5173 && path.startsWith("/api")) {
                URI normalized = new URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        "localhost",
                        serverPort,
                        path,
                        uri.getQuery(),
                        uri.getFragment()
                );
                return normalized.toString();
            }
        } catch (Exception ignored) {
            return baseUrl;
        }
        return baseUrl;
    }

    private String truncateForLog(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_LOG_BODY_CHARS) return text;
        return text.substring(0, MAX_LOG_BODY_CHARS) + "...";
    }

    private void persistImageRequestInfo(Long recordId, String model, String requestUrl) {
        ImageRecord record = imageRecordMapper.selectById(recordId);
        if (record == null) return;
        record.setGenerationModel(model);
        record.setRequestUrl(requestUrl);
        imageRecordMapper.updateById(record);
    }

    private void persistImageError(Long recordId, Integer statusCode, String errorType, String requestUrl, String message) {
        ImageRecord record = imageRecordMapper.selectById(recordId);
        if (record == null) return;
        record.setRequestUrl(requestUrl);
        record.setErrorStatusCode(statusCode);
        record.setErrorType(errorType);
        record.setErrorMessage(truncateErrorMessage(readableErrorMessage(message)));
        imageRecordMapper.updateById(record);
    }

    private String responseErrorType(int statusCode, String body) {
        String jsonType = jsonText(body, "type");
        if (!isBlank(jsonType)) return jsonType;
        String jsonCode = jsonText(body, "code");
        if (!isBlank(jsonCode)) return jsonCode;
        if (body != null && body.toLowerCase().contains("<html")) return "html_error";
        return switch (statusCode) {
            case 400 -> "bad_request";
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found";
            case 405 -> "method_not_allowed";
            case 408 -> "request_timeout";
            case 409 -> "conflict";
            case 413 -> "payload_too_large";
            case 415 -> "unsupported_media_type";
            case 422 -> "unprocessable_entity";
            case 429 -> "rate_limit_error";
            case 500 -> "server_error";
            case 502 -> "bad_gateway";
            case 503 -> "service_unavailable";
            case 504, 524 -> "gateway_timeout";
            default -> statusCode >= 500 ? "upstream_server_error" : "http_error";
        };
    }

    private String errorType(Exception ex) {
        String name = ex.getClass().getSimpleName();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (message.contains("timeout") || message.contains("timed out") || message.contains("超时")) return "timeout";
        if (message.contains("connect") || message.contains("connection")) return "connection_error";
        if (message.contains("json")) return "invalid_response";
        return name;
    }

    private String jsonText(String body, String fieldName) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            JsonNode value = error.isMissingNode() || error.isNull() ? root.path(fieldName) : error.path(fieldName);
            if (value.isMissingNode() || value.isNull()) return "";
            return value.asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String truncateErrorMessage(String text) {
        if (text == null) return "";
        int max = 8000;
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    private String readableErrorMessage(String text) {
        if (text == null || text.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(text);
            JsonNode error = root.path("error");
            JsonNode message = error.isMissingNode() || error.isNull() ? root.path("message") : error.path("message");
            if (!message.isMissingNode() && !message.isNull() && !message.asText("").isBlank()) {
                return message.asText();
            }
        } catch (Exception ignored) {
            return text;
        }
        return text;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String saveRemoteImage(String username, String remoteUrl) throws Exception {
        log.info("开始下载远程图片，urlHost={}", remoteUrl == null ? "" : remoteUrl.replaceFirst("^(https?://[^/]+).*$", "$1"));
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(IMAGE_TIMEOUT)
                .setReadTimeout(IMAGE_TIMEOUT)
                .build();
        long downloadStartedAt = System.currentTimeMillis();
        byte[] bytes = restTemplate.getForObject(remoteUrl, byte[].class);
        log.info("远程图片下载完成，durationMs={}, bytes={}", System.currentTimeMillis() - downloadStartedAt, bytes == null ? 0 : bytes.length);
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("远程图片为空");
        }
        return saveImageBytes(username, bytes);
    }

    private String saveBase64Image(String username, String base64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64);
        if (bytes.length == 0) {
            throw new IllegalStateException("Base64图片为空");
        }
        return saveImageBytes(username, bytes);
    }

    private String saveImageBytes(String username, byte[] bytes) throws Exception {
        Path userDir = UploadPathUtil.resolveImageRoot(imagePath, ImageServiceImpl.class).resolve(username).normalize();
        Files.createDirectories(userDir);
        String fileName = UUID.randomUUID() + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + ".png";
        Files.write(userDir.resolve(fileName), bytes, StandardOpenOption.CREATE_NEW);
        return "/api/images/" + username + "/" + fileName;
    }

    private void saveMetric(Long recordId, Long userId, String qualityCode, long durationMs) {
        ImageGenerationMetric metric = new ImageGenerationMetric();
        metric.setImageRecordId(recordId);
        metric.setUserId(userId);
        metric.setQualityCode(qualityCode);
        metric.setDurationMs(durationMs);
        metric.setCreatedAt(LocalDateTime.now());
        metricMapper.insert(metric);
    }

    private void deleteLocalFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank() || !imageUrl.startsWith("/api/images/")) {
            return;
        }
        try {
            String relative = imageUrl.substring("/api/images/".length());
            Path root = UploadPathUtil.resolveImageRoot(imagePath, ImageServiceImpl.class);
            Path target = root.resolve(relative).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (Exception ignored) {
            // 删除记录不应因为本地文件已经不存在而失败。
        }
    }

}
