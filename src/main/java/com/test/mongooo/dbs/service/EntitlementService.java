package com.bank.dbs.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Entitlement check referenced throughout the architecture diagram (section 2):
 * "check user/customer_cir; check that user/customer_cir is allowed to
 * add/replace/query/delete a supporting doc to application_id".
 *
 * This is a seam: the concrete rule (which claim on the JWT maps to which
 * application_id/customer_cir the caller may touch) is bank-specific entitlement
 * data, typically resolved against an internal entitlements service or a claim
 * embedded in the Azure AD token. Wire the real lookup in here without touching
 * ClientApiController.
 */
@Service
public class EntitlementService {

    public void assertCanAccessApplication(Authentication authentication, String applicationId) {
        // TODO: replace with real entitlement lookup (e.g. call an internal
        // Entitlements API keyed on the JWT's `sub`/`cir` claim, or evaluate a
        // `applications` claim already embedded in the token by Azure AD).
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Not authenticated for application " + applicationId);
        }
    }
}
