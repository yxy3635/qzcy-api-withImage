package com.qzcy.backend.service.impl;

import com.qzcy.backend.entity.MailConfig;
import com.qzcy.backend.entity.PaymentRecord;
import com.qzcy.backend.entity.User;
import com.qzcy.backend.exception.BusinessException;
import com.qzcy.backend.service.MailConfigService;
import com.qzcy.backend.service.MailDeliveryService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Properties;

@Service
@RequiredArgsConstructor
public class MailDeliveryServiceImpl implements MailDeliveryService {
    private final MailConfigService mailConfigService;

    @Override
    public void sendVerificationCode(String email, String scene, String code) {
        MailConfig config = mailConfigService.current();
        if (!isEnabled(config)) {
            return;
        }
        boolean passwordReset = "forgot_password".equals(scene);
        String action = passwordReset ? "找回密码" : "注册账号";
        String content = "<p style=\"margin:0;color:#475569;font-size:15px;line-height:1.8;\">"
                + "你正在进行" + escape(action) + "验证。请在 10 分钟内使用下方验证码，切勿将验证码透露给他人。"
                + "</p>"
                + codeBlock(code)
                + "<p style=\"margin:20px 0 0;color:#94a3b8;font-size:13px;line-height:1.7;\">"
                + "如果这不是你的操作，可以忽略此邮件；你的账户不会因此受到影响。"
                + "</p>";
        send(config, email, brandName(config) + "｜" + action + "验证码", "邮箱验证", "验证码已生成", content);
    }

    @Override
    public void sendRechargeSucceeded(User user, PaymentRecord record) {
        MailConfig config = mailConfigService.current();
        if (!isEnabled(config) || !Boolean.TRUE.equals(config.getRechargeNoticeEnabled())
                || user == null || isBlank(user.getEmail()) || record == null) {
            return;
        }
        String username = isBlank(user.getUsername()) ? user.getEmail() : user.getUsername();
        String content = "<p style=\"margin:0;color:#475569;font-size:15px;line-height:1.8;\">"
                + "你好，" + escape(username) + "。你的充值已到账，账户余额可以立即用于 API 调用。"
                + "</p>"
                + detailTable(new String[][]{
                    {"到账金额", "¥ " + money(record.getAmount())},
                    {"支付方式", paymentType(record.getType())},
                    {"订单编号", "#" + record.getId()}
                })
                + "<p style=\"margin:20px 0 0;color:#94a3b8;font-size:13px;line-height:1.7;\">"
                + "如非本人操作，请尽快联系站点管理员。"
                + "</p>";
        send(config, user.getEmail(), brandName(config) + "｜充值到账通知", "充值到账", "你的余额已更新", content);
    }

    @Override
    public void sendTest(String recipient) {
        if (isBlank(recipient) || !recipient.trim().matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new BusinessException(400, "测试收件人邮箱格式不正确");
        }
        MailConfig config = mailConfigService.current();
        ensureEnabled(config);
        String content = "<p style=\"margin:0;color:#475569;font-size:15px;line-height:1.8;\">"
                + "这是一封邮件模板测试信。若你能看到此页面，SMTP 配置和品牌模板均已生效。"
                + "</p>"
                + detailTable(new String[][]{
                    {"模板样式", "中转站品牌邮件"},
                    {"发送状态", "测试发送成功"}
                });
        send(config, recipient.trim(), brandName(config) + "｜邮件配置测试", "邮件测试", "模板已准备就绪", content);
    }

    private void send(MailConfig config, String recipient, String subject, String eyebrow, String title, String content) {
        JavaMailSenderImpl sender = mailSender(config);
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress(config));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(template(config, eyebrow, title, content), true);
            sender.send(message);
        } catch (Exception ex) {
            throw new BusinessException(500, "邮件发送失败，请检查 SMTP 配置");
        }
    }

    private String template(MailConfig config, String eyebrow, String title, String content) {
        String brand = brandName(config);
        String logo = safeUrl(config.getBrandLogoUrl());
        String siteUrl = safeUrl(config.getSiteUrl());
        String logoMarkup = logo == null
                ? "<div style=\"display:inline-block;width:42px;height:42px;border-radius:12px;background:linear-gradient(135deg,#38bdf8,#2dd4bf);color:#082f49;font-size:15px;font-weight:900;line-height:42px;text-align:center;letter-spacing:-1px;\">IC</div>"
                : "<img src=\"" + logo + "\" width=\"42\" height=\"42\" alt=\"" + escape(brand) + "\" style=\"display:block;width:42px;height:42px;border-radius:12px;object-fit:cover;border:0;\">";
        String brandMarkup = siteUrl == null
                ? "<span style=\"color:#f8fafc;font-size:17px;font-weight:800;letter-spacing:-0.3px;\">" + escape(brand) + "</span>"
                : "<a href=\"" + siteUrl + "\" style=\"color:#f8fafc;font-size:17px;font-weight:800;letter-spacing:-0.3px;text-decoration:none;\">" + escape(brand) + "</a>";
        String button = siteUrl == null ? "" : "<tr><td style=\"padding:0 36px 34px;\"><a href=\"" + siteUrl + "\" style=\"display:inline-block;border-radius:10px;background:#0ea5e9;padding:12px 18px;color:#fff;font-size:14px;font-weight:800;text-decoration:none;\">进入中转站</a></td></tr>";
        return "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f1f5f9;color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Microsoft YaHei',Arial,sans-serif;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">" + escape(title) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:#f1f5f9;padding:32px 12px;\"><tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"max-width:620px;overflow:hidden;border:1px solid #dbeafe;border-radius:18px;background:#fff;box-shadow:0 16px 44px rgba(15,23,42,0.10);\">"
                + "<tr><td style=\"padding:26px 36px;background:linear-gradient(135deg,#0f172a 0%,#0c4a6e 62%,#0f766e 100%);\"><table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\"><tr><td style=\"padding-right:13px;vertical-align:middle;\">" + logoMarkup + "</td><td style=\"vertical-align:middle;\">" + brandMarkup + "<div style=\"margin-top:4px;color:#bae6fd;font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;\">API RELAY SERVICE</div></td></tr></table></td></tr>"
                + "<tr><td style=\"padding:34px 36px 26px;\"><div style=\"margin-bottom:11px;color:#0284c7;font-size:11px;font-weight:900;letter-spacing:1.7px;text-transform:uppercase;\">" + escape(eyebrow) + "</div><h1 style=\"margin:0 0 18px;color:#0f172a;font-size:25px;line-height:1.3;letter-spacing:-0.5px;\">" + escape(title) + "</h1>" + content + "</td></tr>"
                + button
                + "<tr><td style=\"padding:20px 36px;border-top:1px solid #e2e8f0;background:#f8fafc;color:#94a3b8;font-size:12px;line-height:1.7;\">此邮件由 " + escape(brand) + " 自动发送，请勿直接回复。<br>为保障账户安全，请妥善保管你的账号与验证码。</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private String codeBlock(String code) {
        return "<div style=\"margin:24px 0;border:1px solid #bae6fd;border-radius:14px;background:linear-gradient(135deg,#f0f9ff,#ecfeff);padding:18px;text-align:center;\"><div style=\"color:#64748b;font-size:12px;font-weight:800;letter-spacing:1px;\">VERIFICATION CODE</div><div style=\"margin-top:8px;color:#0369a1;font-size:30px;font-weight:900;letter-spacing:8px;line-height:1.15;\">" + escape(code) + "</div></div>";
    }

    private String detailTable(String[][] rows) {
        StringBuilder builder = new StringBuilder("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"margin-top:22px;border:1px solid #dbeafe;border-radius:12px;background:#f8fbff;\">");
        for (String[] row : rows) {
            builder.append("<tr><td style=\"padding:13px 16px;border-bottom:1px solid #e0f2fe;color:#64748b;font-size:13px;font-weight:700;\">")
                    .append(escape(row[0]))
                    .append("</td><td align=\"right\" style=\"padding:13px 16px;border-bottom:1px solid #e0f2fe;color:#0f172a;font-size:14px;font-weight:800;\">")
                    .append(escape(row[1]))
                    .append("</td></tr>");
        }
        return builder.append("</table>").toString();
    }

    private JavaMailSenderImpl mailSender(MailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort() == null ? 587 : config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.ssl.enable", String.valueOf(Boolean.TRUE.equals(config.getSslEnabled())));
        properties.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(config.getStarttlsEnabled())));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.writetimeout", "10000");
        return sender;
    }

    private void ensureEnabled(MailConfig config) {
        if (!isEnabled(config)) {
            throw new BusinessException(400, "请先完成 SMTP 配置并启用邮件发送");
        }
    }

    private boolean isEnabled(MailConfig config) {
        return config != null && Boolean.TRUE.equals(config.getEnabled())
                && !isBlank(config.getHost()) && !isBlank(config.getUsername()) && !isBlank(config.getPassword());
    }

    private String fromAddress(MailConfig config) {
        return isBlank(config.getFromAddress()) ? config.getUsername() : config.getFromAddress();
    }

    private String brandName(MailConfig config) {
        return isBlank(config.getBrandName()) ? "imageCreater · API Relay" : config.getBrandName().trim();
    }

    private String paymentType(String type) {
        return switch (type == null ? "" : type) {
            case "alipay" -> "支付宝";
            case "wxpay", "wechat" -> "微信支付";
            case "qqpay" -> "QQ 钱包";
            default -> "在线支付";
        };
    }

    private String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.stripTrailingZeros().toPlainString();
    }

    private String safeUrl(String value) {
        if (isBlank(value)) {
            return null;
        }
        String url = value.trim();
        return url.matches("^https?://[^\\s<>]+$") ? escape(url) : null;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
