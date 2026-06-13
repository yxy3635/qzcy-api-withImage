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
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final Duration IMAGE_TIMEOUT = Duration.ofMinutes(10);

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
    public ImageRecord submit(Long userId, String username, String prompt, String qualityCode, String size) {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new BusinessException(400, "提示词不能为空");
        }

        ImageGenerationConfig config = configService.requireEnabled(qualityCode);
        String resolvedSize = normalizeRequestedSize(size, config.getSize());
        ImageRecord record = new ImageRecord();
        record.setUserId(userId);
        record.setPrompt(prompt.trim());
        record.setStatus("pending");
        record.setCost(config.getPrice());
        record.setCreatedAt(LocalDateTime.now());
        imageRecordMapper.insert(record);

        try {
            log.info("图像生成任务提交，recordId={}, userId={}, configCode={}, model={}, size={}, quality={}",
                    record.getId(), userId, config.getCode(), config.getModel(), resolvedSize, config.getQuality());
            paymentService.deductBalance(userId, config.getPrice());
            imageGenerationExecutor.execute(() -> runGeneration(record.getId(), username, prompt.trim(), config, resolvedSize));
            return record;
        } catch (BusinessException ex) {
            record.setStatus("failed");
            imageRecordMapper.updateById(record);
            throw ex;
        } catch (Exception ex) {
            record.setStatus("failed");
            imageRecordMapper.updateById(record);
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

    private void runGeneration(Long recordId, String username, String prompt, ImageGenerationConfig config, String size) {
        long startedAt = System.currentTimeMillis();
        log.info("图像生成任务开始执行，recordId={}, thread={}", recordId, Thread.currentThread().getName());
        ImageRecord record = imageRecordMapper.selectById(recordId);
        if (record == null) {
            log.warn("图像生成任务记录不存在，recordId={}", recordId);
            return;
        }

        try {
            String relativePath = requestAndSaveImage(username, prompt, config, size);
            record.setGeneratedImageUrl(relativePath);
            record.setStatus("success");
            imageRecordMapper.updateById(record);
            saveMetric(record.getId(), record.getUserId(), config.getCode(), System.currentTimeMillis() - startedAt);
        } catch (Exception ex) {
            log.warn("图像生成任务失败，recordId={}", recordId, ex);
            record.setStatus("failed");
            imageRecordMapper.updateById(record);
        }
    }

    private String requestAndSaveImage(String username, String prompt, ImageGenerationConfig config, String size) throws Exception {
        HashMap<String, Object> body = new HashMap<>();
        body.put("model", config.getModel());
        body.put("prompt", prompt);
        body.put("n", 1);
        body.put("size", size);
        String effectiveQuality = effectiveQuality(config);
        if (effectiveQuality != null) {
            body.put("quality", effectiveQuality);
        }

        String requestUrl = imageApiUrl(config);
        long requestStartedAt = System.currentTimeMillis();
        log.info("开始请求OpenAI图像接口，url={}, model={}, size={}, quality={}, effectiveQuality={}",
                requestUrl, config.getModel(), size, config.getQuality(), effectiveQuality);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(requestUrl))
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
            if (response.statusCode() == 524) {
                throw new IllegalStateException("中转服务网关超时：同步生图请求在服务商网关层超时，请将 gpt-image 系列质量改为 auto/medium/high，或更换支持长连接/异步任务的中转线路");
            }
            throw new IllegalStateException("OpenAI接口返回错误：" + response.statusCode() + " " + responseBody);
        }

        JsonNode json = objectMapper.readTree(responseBody);
        JsonNode first = json.path("data").path(0);
        if (first == null || first.isMissingNode()) {
            throw new IllegalStateException("OpenAI接口未返回图像数据");
        }
        if (first.hasNonNull("b64_json")) {
            return saveBase64Image(username, first.get("b64_json").asText());
        }
        if (first.hasNonNull("url")) {
            return saveRemoteImage(username, first.get("url").asText());
        }
        throw new IllegalStateException("OpenAI接口返回中没有 url 或 b64_json");
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
        String baseUrl = config.getApiBaseUrl() == null || config.getApiBaseUrl().isBlank()
                ? "https://api.openai.com"
                : config.getApiBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String path = config.getEndpointPath() == null || config.getEndpointPath().isBlank()
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
