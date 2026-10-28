package com.smartdeploy.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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

@Component
public class JenkinsApiKeyFilter extends OncePerRequestFilter {

    @Value("${smartdeploy.jenkins.api-key:}")
    private String expectedApiKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getServletPath();

        return !(
                request.getMethod().equals("GET")
                        && path.matches(
                        "^/api/kubernetes/manifests/deployments/[0-9]+/export$"
                )
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedApiKey =
                request.getHeader("X-SmartDeploy-Api-Key");

        if (providedApiKey != null && !expectedApiKey.isBlank()) {

            boolean valid = MessageDigest.isEqual(
                    expectedApiKey.getBytes(StandardCharsets.UTF_8),
                    providedApiKey.getBytes(StandardCharsets.UTF_8)
            );
            System.out.println("JENKINS API KEY VALID: " + valid);

            if (valid) {

                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                "jenkins-service",
                                null,
                                List.of(
                                        new SimpleGrantedAuthority(
                                                "ROLE_JENKINS"
                                        )
                                )
                        );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}