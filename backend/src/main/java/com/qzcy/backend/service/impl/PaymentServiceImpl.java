package com.qzcy.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qzcy.backend.dto.RechargeDto;
import com.qzcy.backend.entity.PaymentConfig;
import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.mapper.PaymentRecordMapper;
import com.qzcy.backend.mapper.UserMapper;
import com.qzcy.backend.service.PaymentConfigService;
import com.qzcy.backend.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final UserMapper userMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final PaymentConfigService paymentConfigService;

    @Override
    @Transactional
    public void deductBalance(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "扣费金额无效");
        }
        int updated = userMapper.deductBalance(userId, amount);
        if (updated == 0) {
            throw new BusinessException(402, "余额不足，请先充值");
        }
        PaymentRecord record = new PaymentRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setType("balance");
        record.setStatus("completed");
        record.setCreatedAt(LocalDateTime.now());
        paymentRecordMapper.insert(record);
    }

    @Override
    @Transactional
    public Map<String, Object> recharge(Long userId, RechargeDto dto, String backendBaseUrl, String frontendBaseUrl) {
        BigDecimal amount = dto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "充值金额必须大于0");
        }
        PaymentConfig config = paymentConfigService.current();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            throw new BusinessException(400, "第三方支付暂未启用");
        }
        if (isBlank(config.getApiUrl()) || isBlank(config.getMerchantId()) || isBlank(config.getMerchantSecret())) {
            throw new BusinessException(400, "第三方支付配置不完整");
        }
        String payType = normalizePayType(dto.getType());
        if (!isPayTypeEnabled(config, payType)) {
            throw new BusinessException(400, "该支付方式暂未开启");
        }
        PaymentRecord record = new PaymentRecord();
        record.setUserId(userId);
        record.setAmount(amount);
        record.setType(payType);
        record.setCreatedAt(LocalDateTime.now());
        record.setStatus("pending");
        paymentRecordMapper.insert(record);
        String paymentUrl = buildPaymentUrl(config, record, payType, backendBaseUrl, frontendBaseUrl);
        return Map.of(
                "message", "支付订单已创建",
                "paymentUrl", paymentUrl,
                "merchantId", config.getMerchantId(),
                "orderId", record.getId(),
                "amount", amount
        );
    }

    @Override
    @Transactional
    public String handleNotify(Map<String, String> params) {
        PaymentConfig config = paymentConfigService.current();
        if (!verifySign(params, config.getMerchantSecret())) {
            return "fail";
        }
        if (!"TRADE_SUCCESS".equalsIgnoreCase(params.getOrDefault("trade_status", ""))) {
            return "success";
        }
        Long recordId;
        try {
            recordId = Long.valueOf(params.getOrDefault("out_trade_no", ""));
        } catch (NumberFormatException ex) {
            return "fail";
        }
        PaymentRecord record = paymentRecordMapper.selectById(recordId);
        if (record == null || !isThirdPartyRecord(record.getType())) {
            return "fail";
        }
        if ("completed".equals(record.getStatus())) {
            return "success";
        }
        BigDecimal paidAmount;
        try {
            paidAmount = new BigDecimal(params.getOrDefault("money", "0")).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return "fail";
        }
        if (record.getAmount().setScale(2, RoundingMode.HALF_UP).compareTo(paidAmount) != 0) {
            return "fail";
        }
        userMapper.addBalance(record.getUserId(), record.getAmount());
        record.setStatus("completed");
        paymentRecordMapper.updateById(record);
        return "success";
    }

    @Override
    public Page<PaymentRecord> history(Long userId, long page, long size) {
        return paymentRecordMapper.selectPage(
                Page.of(page, size),
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getUserId, userId)
                        .orderByDesc(PaymentRecord::getCreatedAt)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildPaymentUrl(PaymentConfig config, PaymentRecord record, String payType, String backendBaseUrl, String frontendBaseUrl) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pid", config.getMerchantId());
        params.put("type", payType);
        params.put("out_trade_no", String.valueOf(record.getId()));
        params.put("notify_url", backendBaseUrl + "/api/payment/notify");
        params.put("return_url", frontendBaseUrl + "/user/payment");
        params.put("name", "余额充值");
        params.put("money", record.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        params.put("sitename", "imageCreater");
        params.put("sign", sign(params, config.getMerchantSecret()));
        params.put("sign_type", "MD5");
        return normalizeSubmitUrl(config.getApiUrl()) + "?" + toQueryString(params);
    }

    private String normalizePayType(String type) {
        if ("wxpay".equals(type) || "qqpay".equals(type) || "alipay".equals(type)) {
            return type;
        }
        if ("wechat".equals(type)) {
            return "wxpay";
        }
        return "alipay";
    }

    private boolean isPayTypeEnabled(PaymentConfig config, String type) {
        return switch (type) {
            case "alipay" -> Boolean.TRUE.equals(config.getAlipayEnabled());
            case "wxpay" -> Boolean.TRUE.equals(config.getWxpayEnabled());
            case "qqpay" -> Boolean.TRUE.equals(config.getQqpayEnabled());
            default -> false;
        };
    }

    private boolean isThirdPartyRecord(String type) {
        return "third_party".equals(type) || "alipay".equals(type) || "wxpay".equals(type) || "qqpay".equals(type);
    }

    private String normalizeSubmitUrl(String apiUrl) {
        String url = apiUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith(".php")) {
            return url;
        }
        return url + "/submit.php";
    }

    private boolean verifySign(Map<String, String> params, String secret) {
        String sign = params.get("sign");
        if (isBlank(sign) || isBlank(secret)) {
            return false;
        }
        return sign(params, secret).equalsIgnoreCase(sign);
    }

    private String sign(Map<String, String> params, String secret) {
        TreeMap<String, String> sorted = new TreeMap<>();
        params.forEach((key, value) -> {
            if (!"sign".equals(key) && !"sign_type".equals(key) && value != null && !value.isBlank()) {
                sorted.put(key, value);
            }
        });
        StringBuilder builder = new StringBuilder();
        sorted.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(key).append('=').append(value);
        });
        builder.append(secret);
        return md5(builder.toString());
    }

    private String toQueryString(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        params.forEach((key, value) -> {
            if (!builder.isEmpty()) {
                builder.append('&');
            }
            builder.append(urlEncode(key)).append('=').append(urlEncode(value));
        });
        return builder.toString();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte item : bytes) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("MD5签名失败", ex);
        }
    }
}
