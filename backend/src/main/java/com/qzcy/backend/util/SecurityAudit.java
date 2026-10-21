package com.qzcy.backend.util;

import com.qzcy.backend.config.security.JwtUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

/** Never pass credential values, DTOs or request bodies to this logger. */
@Slf4j
public final class SecurityAudit {
    private SecurityAudit() {}

    public static void record(String event, int result, Long targetId, HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = auth != null && auth.getPrincipal() instanceof JwtUserPrincipal p ? p.userId() : null;
        log.info("security event={} result={} userId={} actorId={} ip={} ua={}", event, result, targetId, actorId,
                clean(request.getRemoteAddr()), clean(request.getHeader("User-Agent")));
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.substring(0, Math.min(256, value.length())).replaceAll("[\\p{Cntrl}]", "?");
    }
}
