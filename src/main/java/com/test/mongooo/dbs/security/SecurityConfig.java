package com.bank.dbs.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * AC-BE-01: all /client-api/** and /integration-api/** endpoints require a valid
 * JWT Bearer token, validated against Azure AD's JWKS endpoint; unauthenticated
 * requests return 401. /upload/** and /download/** are intentionally NOT covered by
 * the JWT filter chain — those are HMAC-signed-URL protected instead (validated
 * inside PublicSignedUrlController itself), matching spec 5.1's Auth column
 * ("HMAC token" vs "JWT" / "Service JWT").
 */
@Configuration
@EnableMethodSecurity // enables @PreAuthorize on controllers
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API; no cookies/CSRF surface
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/upload/**", "/download/**").permitAll() // HMAC-validated in-controller
                        .requestMatchers("/client-api/**", "/integration-api/**").authenticated()
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(
                        new ScopeClaimJwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * Validates signature + expiry against Azure AD's JWKS endpoint (spec 2.2:
     * "DBS validates JWT tokens via JWKS endpoint"). issuer-uri drives the JWKS
     * discovery automatically via OIDC well-known configuration.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @org.springframework.beans.factory.annotation.Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuerUri) {
        return NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    }
}
