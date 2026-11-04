package com.smartdeploy.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Authentification service-à-service de Jenkins.
 *
 * Jenkins présente l'en-tête X-SmartDeploy-Api-Key (credential Jenkins
 * "smartdeploy-api-key") sur les deux seules routes qu'il appelle :
 *  - GET  /api/kubernetes/manifests/deployments/{id}/export
 *  - POST /api/webhooks/jenkins
 *
 * Une clé valide donne uniquement le rôle ROLE_JENKINS, que SecurityConfig
 * n'accorde que sur ces deux routes. Une clé absente ou invalide laisse la
 * requête anonyme : Spring Security répond alors 401.
 */
@Component
public class JenkinsApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-SmartDeploy-Api-Key";

    private static final Logger log =
            LoggerFactory.getLogger(JenkinsApiKeyFilter.class);

    private final byte[] expectedApiKey;

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

    public JenkinsApiKeyFilter(
            // Aucune valeur par défaut : l'application refuse de démarrer
            // si SMARTDEPLOY_JENKINS_API_KEY n'est pas fournie.
            @Value("${smartdeploy.jenkins.api-key}") String expectedApiKey
    ) {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalStateException(
                    "smartdeploy.jenkins.api-key est vide : "
                            + "définir la variable SMARTDEPLOY_JENKINS_API_KEY"
            );
        }
        this.expectedApiKey =
                expectedApiKey.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isJenkinsRoute(request);
    }

    static boolean isJenkinsRoute(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getServletPath();

        boolean manifestExport = "GET".equals(method)
                && path.matches(
                "^/api/kubernetes/manifests/deployments/[0-9]+/export$"
        );

        boolean webhook = "POST".equals(method)
                && "/api/webhooks/jenkins".equals(path);

        return manifestExport || webhook;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedApiKey = request.getHeader(API_KEY_HEADER);

        if (providedApiKey != null) {

            boolean valid = MessageDigest.isEqual(
                    expectedApiKey,
                    providedApiKey.trim().getBytes(StandardCharsets.UTF_8)
            );

            if (valid) {
                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                "jenkins-service",
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_JENKINS"))
                        );
                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            } else {
                // Ne jamais journaliser la clé reçue.
                log.warn(
                        "Clé API Jenkins invalide sur {} {} depuis {}",
                        request.getMethod(),
                        request.getServletPath(),
                        request.getRemoteAddr()
                );
                publishAudit(new com.smartdeploy.audit.AuditEvent(
                        com.smartdeploy.audit.AuditAction.API_KEY_REJECTED,
                        "Appelant inconnu", "API", null, null, null,
                        "Clé API Jenkins refusée sur " + request.getMethod() + " " + request.getServletPath(),
                        "La clé reçue n'est jamais enregistrée", true
                ));
            }
        }

        filterChain.doFilter(request, response);
    }
}
