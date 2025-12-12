package com.slopeoasis.post.interceptor;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Value("${jwt.issuer:}")
    private String issuer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
            return false;
        }

        String token = authHeader.substring(7);
        String userId = extractUserIdFromJwt(token);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid JWT token\"}");
            return false;
        }

        // Store userId in request attribute for use in controller
        request.setAttribute("X-User-Id", userId);
        return true;
    }

    private String extractUserIdFromJwt(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;

            // Decode payload (second part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode claims = objectMapper.readTree(payload);

            // Extract 'sub' (subject/user ID) claim
            JsonNode subClaim = claims.get("sub");
            if (subClaim != null && !subClaim.asText().isEmpty()) {
                return subClaim.asText();
            }

            // Fallback to 'user_id' if 'sub' is not present
            JsonNode userIdClaim = claims.get("user_id");
            if (userIdClaim != null && !userIdClaim.asText().isEmpty()) {
                return userIdClaim.asText();
            }

            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
