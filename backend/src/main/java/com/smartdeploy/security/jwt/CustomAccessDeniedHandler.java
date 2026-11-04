package com.smartdeploy.security.jwt;

import com.smartdeploy.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public CustomAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Facultatif : absent dans les tests unitaires qui construisent la classe directement. */
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private void publishAudit(com.smartdeploy.audit.AuditEvent event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }


    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        ApiError apiError = new ApiError(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "Vous n'avez pas les permissions nécessaires",
                Map.of()
        );

        publishAudit(new com.smartdeploy.audit.AuditEvent(
                com.smartdeploy.audit.AuditAction.ACCESS_DENIED,
                null, null, null, null, null,
                "Accès refusé : " + request.getMethod() + " " + request.getServletPath(),
                "Permissions insuffisantes pour cette action", true
        ));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(), apiError);
    }
}