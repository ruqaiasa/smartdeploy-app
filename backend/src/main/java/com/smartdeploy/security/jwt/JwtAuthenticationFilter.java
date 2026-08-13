package com.smartdeploy.security.jwt;

import com.smartdeploy.user.CustomUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final CustomUserDetailsService
            userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService
    ) {
        this.jwtService = jwtService;

        this.userDetailsService =
                userDetailsService;
    }

    /* ==========================================================
     *              ROUTES IGNORÉES PAR LE FILTRE JWT
     * ==========================================================
     */

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {

        String path =
                request.getServletPath();

        return path.equals(
                "/api/webhooks/jenkins"
        );
    }

    /* ==========================================================
     *                  JWT AUTHENTICATION
     * ==========================================================
     */

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader(
                        HttpHeaders.AUTHORIZATION
                );

        /*
         * Une requête sans token continue normalement.
         *
         * Spring Security décidera ensuite si la route
         * est publique ou protégée.
         */
        if (
                authorizationHeader == null
                        || !authorizationHeader
                        .startsWith("Bearer ")
        ) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        String token =
                authorizationHeader
                        .substring(7);

        try {

            String email =
                    jwtService
                            .extractUsername(token);

            boolean authenticationMissing =
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication()
                            == null;

            if (
                    email != null
                            && authenticationMissing
            ) {

                UserDetails userDetails =
                        userDetailsService
                                .loadUserByUsername(
                                        email
                                );

                boolean tokenValid =
                        jwtService
                                .isTokenValid(
                                        token,
                                        userDetails
                                );

                if (tokenValid) {

                    UsernamePasswordAuthenticationToken
                            authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails
                                            .getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(
                                            request
                                    )
                    );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(
                                    authentication
                            );
                }
            }

        } catch (
                JwtException
                | IllegalArgumentException exception
        ) {

            /*
             * Token expiré, invalide,
             * malformé ou signé avec
             * une mauvaise clé.
             */
            SecurityContextHolder
                    .clearContext();
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}