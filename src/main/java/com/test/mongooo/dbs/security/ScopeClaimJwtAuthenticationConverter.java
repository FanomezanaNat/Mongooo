package com.bank.dbs.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Maps Azure AD token claims to Spring Security authorities. Consuming
 * applications (Non-Indiv Onboarding, Trade Finance/Catalyst, etc.) authenticate
 * with a service principal whose token carries a `roles` claim containing
 * "service" — mapped here to authority SCOPE_service, which
 * IntegrationApiController/PdfMergeController require via @PreAuthorize.
 *
 * End-user (bank operator) tokens instead carry standard `scp`/`scope` claims;
 * ClientApiController relies on plain .authenticated() plus EntitlementService's
 * per-application check rather than a coarse scope, since operator entitlement is
 * application_id-specific, not a static role.
 */
public class ScopeClaimJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.forEach(role -> authorities.add(new SimpleGrantedAuthority("SCOPE_" + role)));
        }

        String scopeClaim = jwt.getClaimAsString("scp");
        if (scopeClaim != null) {
            for (String scope : scopeClaim.split(" ")) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope));
            }
        }

        return new JwtAuthenticationToken(jwt, authorities);
    }
}
