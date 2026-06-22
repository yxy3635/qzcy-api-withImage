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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
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

    @GetMapping("/v1/models")
    public ResponseEntity<JsonNode> models(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                           @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                           @RequestParam(value = "key", required = false) String queryKey,
                                           HttpServletRequest request) {
        RelayToken access;
        try {
            access = relayPolicyService.requireRelayToken(authorization, apiKeyHeader, queryKey);
            relayPolicyService.enforceIpAccess(access, clientIp(request));
        } catch (BusinessException ex) {
            return relayError(ex.getCode(), ex.getMessage());
        }

        Set<String> groupModels = accessibleModelsForGroups(access.getGroupNames());
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode data = objectMapper.createArrayNode();
        Set<String> returnedModels = new HashSet<>();
        modelMapper.selectList(new QueryWrapper<RelayModel>()
                        .eq("enabled", true)
                        .orderByAsc("sort_order")
                        .orderByAsc("id"))
                .stream()
                .filter(model -> groupModels.contains(model.getModel()))
                .filter(model -> containsCsv(access.getAllowedModels(), model.getModel()))
                .filter(model -> returnedModels.add(model.getModel()))
                .forEach(model -> {
                    ObjectNode item = objectMapper.createObjectNode();
                    item.put("id", model.getModel());
                    item.put("object", "model");
                    item.put("created", 0);
                    item.put("owned_by", "relay");
                    data.add(item);
                });
        body.put("object", "list");
        body.set("data", data);
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

    @PostMapping("/v1/responses")
    public ResponseEntity<?> responses(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                       @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                       @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                       @RequestParam(value = "key", required = false) String queryKey,
                                       HttpServletRequest request,
                                       @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "chat", "/v1/responses", body);
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

    @PostMapping("/v1/images/generations")
    public ResponseEntity<?> imageGenerations(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                              @RequestHeader(value = "x-api-key", required = false) String apiKeyHeader,
                                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                                              @RequestParam(value = "key", required = false) String queryKey,
                                              HttpServletRequest request,
                                              @RequestBody ObjectNode body) throws Exception {
        return relayJson(authorization, apiKeyHeader, queryKey, userAgent, clientIp(request), "image", "/v1/images/generations", body);
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
        try {
            RelayDispatchResult result = relayDispatchService.dispatch(new RelayDispatchRequest(
                    authorization,
                    apiKeyHeader,
                    queryKey,
                    userAgent,
                    clientIp,
                    endpointType,
                    path,
                    body
            ));
            return ResponseEntity.status(result.statusCode())
                    .contentType(mediaType(result.contentType()))
                    .body(result.body());
        } catch (BusinessException ex) {
            return relayError(ex.getCode(), ex.getMessage());
        }
    }

    private ResponseEntity<JsonNode> relayError(int code, String message) {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode error = objectMapper.createObjectNode();
        error.put("message", message);
        error.put("type", code == 429 ? "rate_limit_error" : "relay_error");
        error.put("code", code);
        body.set("error", error);
        return ResponseEntity.status(code)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
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
                if (configuredModels == null || configuredModels == 0 || "default".equalsIgnoreCase(group.getCode())) {
                    models.addAll(channelModelMapper.enabledModelNamesForGroup(group.getCode()));
                } else {
                    Set<String> channelModels = new HashSet<>(channelModelMapper.enabledModelNamesForGroup(group.getCode()));
                    groupModelMapper.modelsForGroup(group.getId()).stream()
                            .filter(channelModels::contains)
                            .forEach(models::add);
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
}
