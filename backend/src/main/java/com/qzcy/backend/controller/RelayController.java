package com.qzcy.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.qzcy.backend.dto.ApiResponse;
import com.qzcy.backend.dto.RelayTokenCreateDto;
import com.qzcy.backend.dto.RelayTokenDto;
import com.qzcy.backend.dto.RelayUserOverviewDto;
import com.qzcy.backend.dto.relay.RelayDispatchRequest;
import com.qzcy.backend.dto.relay.RelayDispatchResult;
import com.qzcy.backend.dto.relay.RelayMultipartFile;
import com.qzcy.backend.dto.relay.RelayStreamDispatchResult;
import com.qzcy.backend.entity.RelayGroup;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayToken;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.RelayGroupMapper;
import com.qzcy.backend.mapper.RelayGroupModelMapper;
import com.qzcy.backend.mapper.RelayChannelModelMapper;
import com.qzcy.backend.mapper.RelayModelMapper;
import com.qzcy.backend.service.RelayChannelStatusService;
import com.qzcy.backend.service.RelayDispatchService;
import com.qzcy.backend.service.RelayPolicyService;
import com.qzcy.backend.service.RelayService;
import com.qzcy.backend.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class RelayController {
    private final RelayService relayService;
    private final RelayDispatchService relayDispatchService;
    private final RelayPolicyService relayPolicyService;
    private final RelayChannelStatusService relayChannelStatusService;
    private final RelayModelMapper modelMapper;
    private final RelayGroupMapper groupMapper;
    private final RelayGroupModelMapper groupModelMapper;
    private final RelayChannelModelMapper channelModelMapper;
    private final ObjectMapper objectMapper;

    @GetMapping("/relay/overview")
    public ApiResponse<RelayUserOverviewDto> overview() {
        return ApiResponse.success(relayService.userOverview(SecurityUtil.current().userId()));
    }

    @PostMapping("/relay/tokens")
    public ApiResponse<RelayTokenDto> createToken(@RequestBody RelayTokenCreateDto dto) {
        return ApiResponse.success(relayService.createToken(SecurityUtil.current().userId(), dto));
    }

    @PutMapping("/relay/tokens/{id}")
    public ApiResponse<RelayTokenDto> updateToken(@PathVariable Long id, @RequestBody RelayTokenCreateDto dto) {
        return ApiResponse.success(relayService.updateToken(SecurityUtil.current().userId(), id, dto));
    }

    @DeleteMapping("/relay/tokens/{id}")
    public ApiResponse<Void> deleteToken(@PathVariable Long id) {
        relayService.deleteToken(SecurityUtil.current().userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/relay/channels/status/sync")
    public ApiResponse<Void> syncChannelStatus() {
        relayChannelStatusService.syncAll();
        return ApiResponse.success(null);
    }

    @GetMapping({"/v1/models", "/models", "/v1/v1/models"})
    public ResponseEntity<JsonNode> models(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                           @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                           @RequestHeader(value = "anthropic-version", required = false) String anthropicVersion,
                                           @RequestParam(value = "key", required = false) String queryKey,
                                           HttpServletRequest request) {
        boolean anthropicFormat = isAnthropicRequest(authorization, apiKeyHeader, anthropicVersion, "/v1/models");
        log.info("Relay models request uri={} query={} ip={} ua={} auth={} xApiKey={} queryKey={} anthropicVersion={} responseFormat={}",
                request.getRequestURI(),
                request.getQueryString(),
                clientIp(request),
                safeHeader(request.getHeader(HttpHeaders.USER_AGENT)),
                credentialSummary(authorization),
                credentialSummary(apiKeyHeader),
                credentialSummary(queryKey),
                safeHeader(anthropicVersion),
                anthropicFormat ? "anthropic" : "openai");
        RelayToken access;
        try {
            access = relayPolicyService.requireRelayToken(authorization, apiKeyHeader, queryKey);
            relayPolicyService.enforceIpAccess(access, clientIp(request));
        } catch (BusinessException ex) {
            log.warn("Relay models local error uri={} status={} format={} message={}",
                    request.getRequestURI(),
                    ex.getCode(),
                    anthropicFormat ? "anthropic" : "openai",
                    ex.getMessage());
            return relayError(ex.getCode(), ex.getMessage(), anthropicFormat);
        }

        boolean responseAnthropicFormat = anthropicFormat || looksLikeAnthropicGroups(access.getGroupNames());
        Set<String> groupModels = accessibleModelsForGroups(access.getGroupNames());
        boolean unrestrictedModels = groupModels.isEmpty();
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode data = objectMapper.createArrayNode();
        Set<String> returnedModels = new HashSet<>();
        modelMapper.selectList(new QueryWrapper<RelayModel>()
                        .eq("enabled", true)
                        .orderByAsc("sort_order")
                        .orderByAsc("id"))
                .stream()
                .filter(model -> unrestrictedModels || groupModels.contains(publicModelName(model)))
                .filter(model -> containsCsv(access.getAllowedModels(), publicModelName(model))
                        || containsCsv(access.getAllowedModels(), model.getModel()))
                .filter(model -> returnedModels.add(publicModelName(model)))
                .forEach(model -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    if (responseAnthropicFormat) {
                        item.put("id", publicModelName(model));
                        item.put("type", "model");
                        item.put("display_name", publicModelName(model));
                        item.put("created_at", "1970-01-01T00:00:00Z");
                    } else {
                        item.put("id", publicModelName(model));
                        item.put("object", "model");
                        item.put("created", 0);
                        item.put("owned_by", "relay");
                    }
                    data.add(item);
                });
        if (responseAnthropicFormat) {
            body.set("data", data);
            body.put("first_id", data.isEmpty() ? "" : data.get(0).path("id").asText(""));
            body.put("last_id", data.isEmpty() ? "" : data.get(data.size() - 1).path("id").asText(""));
            body.put("has_more", false);
        } else {
            body.put("object", "list");
            body.set("data", data);
        }
        log.info("Relay models response uri={} tokenId={} groups={} format={} accessibleCount={} returnedCount={} first={} last={}",
                request.getRequestURI(),
                access.getId(),
                access.getGroupNames(),
                responseAnthropicFormat ? "anthropic" : "openai",
                groupModels.size(),
                data.size(),
                data.isEmpty() ? "" : data.get(0).path("id").asText(""),
                data.isEmpty() ? "" : data.get(data.size() - 1).path("id").asText(""));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    @PostMapping("/v1/chat/completions")
    public ResponseEntity<?> chatCompletions(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                             @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                             @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                             @RequestParam(value = "key", required = false) String queryKey,
                                             HttpServletRequest request,
                                             @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "chat", "/v1/chat/completions", body);
    }

    @PostMapping({"/v1/messages", "/messages", "/v1/v1/messages"})
    public ResponseEntity<?> anthropicMessages(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                               @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                               @RequestHeader(value = "anthropic-version", required = false) String anthropicVersion,
                                               @RequestHeader(value = "anthropic-beta", required = false) String anthropicBeta,
                                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                               @RequestParam(value = "key", required = false) String queryKey,
                                               HttpServletRequest request,
                                               @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), anthropicVersion, anthropicBeta, "chat", "/v1/messages", body);
    }

    @PostMapping({"/v1/messages/count_tokens", "/messages/count_tokens", "/v1/v1/messages/count_tokens"})
    public ResponseEntity<?> anthropicCountTokens(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                  @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                                  @RequestHeader(value = "anthropic-version", required = false) String anthropicVersion,
                                                  @RequestHeader(value = "anthropic-beta", required = false) String anthropicBeta,
                                                  @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                  @RequestParam(value = "key", required = false) String queryKey,
                                                  HttpServletRequest request,
                                                  @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), anthropicVersion, anthropicBeta, "chat", "/v1/messages/count_tokens", body);
    }

    @PostMapping("/v1/responses")
    public ResponseEntity<?> responses(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                       @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                       @RequestParam(value = "key", required = false) String queryKey,
                                       HttpServletRequest request,
                                       @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), responsesEndpointType(body), "/v1/responses", body);
    }

    @PostMapping("/v1/responses/compact")
    public ResponseEntity<?> responsesCompact(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                              @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                              @RequestParam(value = "key", required = false) String queryKey,
                                              HttpServletRequest request,
                                              @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "chat", "/v1/responses/compact", body);
    }

    @PostMapping("/v1/completions")
    public ResponseEntity<?> completions(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                         @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                         @RequestParam(value = "key", required = false) String queryKey,
                                         HttpServletRequest request,
                                         @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "chat", "/v1/completions", body);
    }

    @PostMapping("/v1/embeddings")
    public ResponseEntity<?> embeddings(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                        @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                        @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                        @RequestParam(value = "key", required = false) String queryKey,
                                        HttpServletRequest request,
                                        @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "embedding", "/v1/embeddings", body);
    }

    @PostMapping(value = "/v1/images/generations", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> imageGenerations(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                              @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                              @RequestParam(value = "key", required = false) String queryKey,
                                              HttpServletRequest request,
                                              @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "image", "/v1/images/generations", body);
    }

    @PostMapping(value = "/v1/images/generations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> imageGenerationsMultipart(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                       @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                       @RequestParam(value = "key", required = false) String queryKey,
                                                       HttpServletRequest request,
                                                       @RequestParam Map<String, String> form,
                                                       @RequestParam(value = "image", required = false) List<MultipartFile> imageFiles) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        form.forEach(body::put);
        List<RelayMultipartFile> files = imageFiles == null ? List.of() : imageFiles.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> {
                    try {
                        return new RelayMultipartFile(
                                "image",
                                file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "reference.png" : file.getOriginalFilename(),
                                file.getContentType() == null || file.getContentType().isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                                file.getBytes()
                        );
                    } catch (Exception ex) {
                        throw new BusinessException(400, "参考图读取失败：" + ex.getMessage());
                    }
                })
                .toList();
        return relayMultipart(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "image", "/v1/images/generations", body, files);
    }

    @PostMapping(value = "/v1/images/edits", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> imageEditsMultipart(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                 @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                 @RequestParam(value = "key", required = false) String queryKey,
                                                 HttpServletRequest request,
                                                 @RequestParam Map<String, String> form,
                                                 @RequestParam(value = "image", required = false) List<MultipartFile> imageFiles,
                                                 @RequestParam(value = "image[]", required = false) List<MultipartFile> imageArrayFiles) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        form.forEach(body::put);
        List<RelayMultipartFile> files = multipartFiles(imageFiles, imageArrayFiles);
        return relayMultipart(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "image", "/v1/images/edits", body, files);
    }

    @PostMapping("/v1/moderations")
    public ResponseEntity<?> moderations(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                         @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                         @RequestParam(value = "key", required = false) String queryKey,
                                         HttpServletRequest request,
                                         @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "chat", "/v1/moderations", body);
    }

    @PostMapping("/v1/audio/transcriptions")
    public ResponseEntity<?> audioTranscriptions(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                                 @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                                 @RequestParam(value = "key", required = false) String queryKey,
                                                 HttpServletRequest request,
                                                 @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "audio", "/v1/audio/transcriptions", body);
    }

    @PostMapping("/v1/audio/translations")
    public ResponseEntity<?> audioTranslations(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                               @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                               @RequestParam(value = "key", required = false) String queryKey,
                                               HttpServletRequest request,
                                               @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "audio", "/v1/audio/translations", body);
    }

    @PostMapping("/v1/audio/speech")
    public ResponseEntity<?> audioSpeech(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                         @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                         @RequestParam(value = "key", required = false) String queryKey,
                                         HttpServletRequest request,
                                         @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "audio", "/v1/audio/speech", body);
    }

    @PostMapping("/v1/{resource}")
    public ResponseEntity<?> genericJson(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                         @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                         @RequestParam(value = "key", required = false) String queryKey,
                                         HttpServletRequest request,
                                         @PathVariable String resource,
                                         @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "chat", "/v1/" + resource, body);
    }

    private ResponseEntity<?> relayJson(String authorization, String apiKeyHeader, String queryKey, String userAgent, String clientIp, String endpointType, String path, ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp, null, null, endpointType, path, body);
    }

    private ResponseEntity<?> relayJson(String authorization, String apiKeyHeader, String queryKey, String userAgent, String clientIp, String anthropicVersion, String anthropicBeta, String endpointType, String path, ObjectNode body) throws Exception {
        boolean anthropicFormat = isAnthropicRequest(authorization, apiKeyHeader, anthropicVersion, path);
        log.debug("Relay dispatch request path={} endpointType={} model={} stream={} ip={} ua={} auth={} xApiKey={} queryKey={} anthropicVersion={} anthropicBeta={} responseFormat={}",
                path,
                endpointType,
                body == null ? "" : body.path("model").asText(""),
                body != null && body.path("stream").asBoolean(false),
                clientIp,
                safeHeader(userAgent),
                credentialSummary(authorization),
                credentialSummary(apiKeyHeader),
                credentialSummary(queryKey),
                safeHeader(anthropicVersion),
                safeHeader(anthropicBeta),
                anthropicFormat ? "anthropic" : "openai");
        try {
            if (body.path("stream").asBoolean(false)) {
                RelayStreamDispatchResult result = relayDispatchService.dispatchStream(new RelayDispatchRequest(
                        authorization,
                        apiKeyHeader,
                        queryKey,
                        userAgent,
                        clientIp,
                        anthropicVersion,
                        anthropicBeta,
                        endpointType,
                        path,
                        body
                ));
                return writeStreamResult(result, path);
            }
            RelayDispatchResult result = relayDispatchService.dispatch(new RelayDispatchRequest(
                    authorization,
                    apiKeyHeader,
                    queryKey,
                    userAgent,
                    clientIp,
                    anthropicVersion,
                    anthropicBeta,
                    endpointType,
                    path,
                    body
            ));
            return ResponseEntity.status(result.statusCode())
                    .contentType(mediaType(result.contentType()))
                    .body(result.body());
        } catch (BusinessException ex) {
            log.warn("Relay dispatch local error path={} status={} format={} model={} message={}",
                    path,
                    ex.getCode(),
                    anthropicFormat ? "anthropic" : "openai",
                    body == null ? "" : body.path("model").asText(""),
                    ex.getMessage());
            return relayError(ex.getCode(), ex.getMessage(), anthropicFormat);
        }
    }

    @org.springframework.lang.Nullable
    private ResponseEntity<?> writeStreamResult(RelayStreamDispatchResult result, String path) throws Exception {
        // 直接写到 HttpServletResponse，绕开 Spring 按方法声明返回类型解析 ReturnValueHandler 的坑：
        // relayJson 声明返回 ResponseEntity<?>，其泛型被擦除成 Object，StreamingResponseBodyReturnValueHandler
        // 不会认流式 body，最终落到 HttpEntityMethodProcessor 找消息转换器 → 没有 text/event-stream 的转换器 → 500
        // "No converter for ... with preset Content-Type 'text/event-stream'"，客户端就"用不了/api错误"。
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null || attributes.getResponse() == null) {
            throw new IllegalStateException("No current servlet response for stream relay path=" + path);
        }
        HttpServletResponse response = attributes.getResponse();
        response.setStatus(result.statusCode());
        String contentType = result.contentType();
        response.setContentType(contentType == null || contentType.isBlank() ? MediaType.TEXT_EVENT_STREAM_VALUE : contentType);
        try {
            result.body().writeTo(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception ex) {
            // 多为客户端中途断开；上游收发 lambda 内部 finally 已释放并发闸并落账 usage，这里只记录、不再上抛，
            // 避免在响应已提交后再触发 Spring 的错误处理造成二次报错。
            log.warn("Relay stream write interrupted path={} status={} message={}", path, result.statusCode(), ex.getMessage());
        }
        return null;
    }

    private ResponseEntity<?> relayMultipart(String authorization, String apiKeyHeader, String queryKey, String userAgent, String clientIp, String endpointType, String path, ObjectNode body, List<RelayMultipartFile> files) throws Exception {
        try {
            RelayDispatchResult result = relayDispatchService.dispatch(new RelayDispatchRequest(
                    authorization,
                    apiKeyHeader,
                    queryKey,
                    userAgent,
                    clientIp,
                    endpointType,
                    path,
                    body,
                    files
            ));
            return ResponseEntity.status(result.statusCode())
                    .contentType(mediaType(result.contentType()))
                    .body(result.body());
        } catch (BusinessException ex) {
            return relayError(ex.getCode(), ex.getMessage());
        }
    }

    private List<RelayMultipartFile> multipartFiles(List<MultipartFile> imageFiles, List<MultipartFile> imageArrayFiles) {
        List<MultipartFile> source = new java.util.ArrayList<>();
        if (imageFiles != null) source.addAll(imageFiles);
        if (imageArrayFiles != null) source.addAll(imageArrayFiles);
        return source.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> {
                    try {
                        return new RelayMultipartFile(
                                "image",
                                file.getOriginalFilename() == null || file.getOriginalFilename().isBlank() ? "reference.png" : file.getOriginalFilename(),
                                file.getContentType() == null || file.getContentType().isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType(),
                                file.getBytes()
                        );
                    } catch (Exception ex) {
                        throw new BusinessException(400, "参考图读取失败：" + ex.getMessage());
                    }
                })
                .toList();
    }

    private String responsesEndpointType(ObjectNode body) {
        JsonNode tools = body == null ? null : body.path("tools");
        if (tools != null && tools.isArray()) {
            for (JsonNode tool : tools) {
                if ("image_generation".equalsIgnoreCase(tool.path("type").asText(""))) {
                    return "image";
                }
            }
        }
        return "chat";
    }

    private ResponseEntity<JsonNode> relayError(int code, String message) {
        return relayError(code, message, false);
    }

    private ResponseEntity<JsonNode> relayError(int code, String message, boolean anthropicFormat) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode error = objectMapper.createObjectNode();
        if (anthropicFormat) {
            body.put("type", "error");
            error.put("type", anthropicErrorType(code));
            error.put("message", message);
            body.set("error", error);
            return ResponseEntity.status(code)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);
        }
        error.put("message", message);
        error.put("type", code == 429 ? "rate_limit_error" : "relay_error");
        error.put("code", code);
        body.set("error", error);
        return ResponseEntity.status(code)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private boolean isAnthropicRequest(String authorization, String apiKeyHeader, String anthropicVersion, String path) {
        if (anthropicVersion != null && !anthropicVersion.isBlank()) return true;
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) return true;
        if (path != null && path.startsWith("/v1/messages")) return true;
        return false;
    }

    private boolean looksLikeAnthropicGroups(String groupNames) {
        return csvValues(groupNames).stream()
                .map(String::toLowerCase)
                .anyMatch(group -> group.contains("anthropic")
                        || group.contains("anthrotic")
                        || group.contains("claude"));
    }

    private String anthropicErrorType(int code) {
        return switch (code) {
            case 400 -> "invalid_request_error";
            case 401 -> "authentication_error";
            case 403 -> "permission_error";
            case 404 -> "not_found_error";
            case 429 -> "rate_limit_error";
            default -> "api_error";
        };
    }

    private MediaType mediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) return MediaType.APPLICATION_JSON;
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_JSON;
        }
    }

    private Set<String> accessibleModelsForGroups(String groupNames) {
        Set<String> models = new HashSet<>();
        csvValues(groupNames == null || groupNames.isBlank() ? "default" : groupNames).forEach(code -> {
            RelayGroup group = groupMapper.selectOne(new QueryWrapper<RelayGroup>().eq("code", code).eq("enabled", true));
            if (group != null) {
                Long configuredModels = groupModelMapper.countEnabledModelsForGroup(group.getId());
                if (configuredModels != null && configuredModels > 0 && !"default".equalsIgnoreCase(group.getCode())) {
                    groupModelMapper.modelsForGroup(group.getId()).forEach(models::add);
                } else {
                    models.addAll(channelModelMapper.enabledModelNamesForGroup(group.getCode()));
                }
            }
        });
        return models;
    }

    private boolean containsCsv(String csv, String value) {
        if (value == null || value.isBlank()) return true;
        if (csv == null || csv.isBlank()) return true;
        return csvValues(csv).stream().anyMatch(item -> item.equalsIgnoreCase(value.trim()));
    }

    private String publicModelName(RelayModel model) {
        if (model == null) return "";
        String displayName = model.getDisplayName();
        return displayName == null || displayName.isBlank() ? model.getModel() : displayName;
    }

    private java.util.List<String> csvValues(String csv) {
        if (csv == null || csv.isBlank()) return java.util.List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) return realIp.trim();
        return request.getRemoteAddr();
    }

    private String credentialSummary(String value) {
        if (value == null || value.isBlank()) return "absent";
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            normalized = normalized.substring("Bearer ".length()).trim();
        }
        if (normalized.length() <= 8) return "present(" + normalized.length() + ")";
        return "present(" + normalized.substring(0, 4) + "..." + normalized.substring(normalized.length() - 4) + ",len=" + normalized.length() + ")";
    }

    private String safeHeader(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().replaceAll("[\\r\\n\\t]+", " ");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 157) + "...";
    }
}
