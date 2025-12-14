package com.slopeoasis.post.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.slopeoasis.post.clerk.ClerkJwtVerifier;
import com.slopeoasis.post.clerk.ClerkTokenPayload;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Uses Clerk verifier for proper JWT validation. Sets X-User-Id (usid) in request attributes
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private ClerkJwtVerifier clerkJwtVerifier;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Skip OPTIONS requests (CORS preflight) - they don't have Authorization header
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Missing or invalid Authorization header\"}");
            return false;
        }

        String token = authHeader.substring(7);
        try {
            ClerkTokenPayload payload = clerkJwtVerifier.verify(token);
            
            // Set user ID in request attribute for controllers to use
            String usid = payload.getUsid();
            System.out.println("[JwtInterceptor] Token verified successfully, usid: " + usid);
            request.setAttribute("X-User-Id", usid);
            
            return true;
        } catch (Exception e) {
            System.err.println("[JwtInterceptor] JWT verification failed: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Invalid or expired token: " + e.getMessage() + "\"}");
            return false;
        }
    }
}
