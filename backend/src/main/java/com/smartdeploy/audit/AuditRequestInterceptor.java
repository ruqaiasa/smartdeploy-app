package com.smartdeploy.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Trace automatiquement toute requête qui modifie des données, avec son
 * auteur, son adresse IP, son navigateur et son résultat. Une nouvelle
 * fonctionnalité est donc auditée sans code supplémentaire.
 * Les refus d'accès (403) sont tracés par CustomAccessDeniedHandler.
 */
@Component
public class AuditRequestInterceptor implements HandlerInterceptor {

    private final ApplicationEventPublisher eventPublisher;

    public AuditRequestInterceptor(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        String method = request.getMethod();
        String path = request.getServletPath();

        if (!AuditRoutes.isMutating(method) || AuditRoutes.isIgnored(path)) {
            return;
        }
        if (exception instanceof AccessDeniedException) {
            return;
        }

        int status = exception != null && response.getStatus() < 400 ? 500 : response.getStatus();
        if (status == 401 || status == 403) {
            return;
        }

        AuditRoutes.RouteAudit route = AuditRoutes.describe(method, path);

        if (status >= 400) {
            eventPublisher.publishEvent(new AuditEvent(
                    AuditAction.ACTION_FAILED, null, route.targetType(), route.targetId(), null, null,
                    "Échec : " + route.summary() + " (HTTP " + status + ")", method + " " + path, false
            ));
            return;
        }

        if (AuditRoutes.isCoveredByDomainEvents(method, path) || route.skipped()) {
            return;
        }

        eventPublisher.publishEvent(new AuditEvent(
                route.action(), null, route.targetType(), route.targetId(), null, null,
                route.summary(), method + " " + path, false
        ));
    }
}
