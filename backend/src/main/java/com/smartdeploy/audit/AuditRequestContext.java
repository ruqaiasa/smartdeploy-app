package com.smartdeploy.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Adresse IP et navigateur de la requête HTTP en cours, s'il y en a une
 * (absents pour une tâche planifiée comme le garde-fou).
 */
public record AuditRequestContext(String ipAddress, String userAgent) {

    static final AuditRequestContext NONE = new AuditRequestContext(null, null);

    public static AuditRequestContext current() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return NONE;
        }
        return from(servletAttributes.getRequest());
    }

    static AuditRequestContext from(HttpServletRequest request) {
        return new AuditRequestContext(clientIp(request), truncate(request.getHeader("User-Agent"), 255));
    }

    /**
     * L'en-tête X-Forwarded-For n'est pris en compte que derrière un proxy
     * local (serveur de développement Vite) : sinon un client pourrait
     * falsifier son adresse.
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        boolean localProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        if (localProxy && forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 64);
        }
        return truncate(remote, 64);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
