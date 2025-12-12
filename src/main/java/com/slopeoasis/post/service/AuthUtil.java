package com.slopeoasis.post.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthUtil {

    // Default claim name for user id. Can be overridden if needed.
    private static final String DEFAULT_USER_ID_CLAIM = "sub";

    public String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            Object claim = jwt.getClaim(DEFAULT_USER_ID_CLAIM);
            return claim != null ? claim.toString() : null;
        }
        return authentication.getName();
    }
}
