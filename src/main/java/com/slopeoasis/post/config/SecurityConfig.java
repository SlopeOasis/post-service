package com.slopeoasis.post.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.slopeoasis.post.interceptor.JwtInterceptor;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Apply JWT validation to all endpoints except public reads
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/posts/**")
                .excludePathPatterns(
                    "/posts/search/**",
                    "/posts/tag/**",
                    "/posts/themes",
                    "GET /posts/{id}",
                    "GET /posts/{id}/ratings",
                    "GET /posts/{id}/ratings/summary",
                    "GET /posts/{id}/availability"
                );
    }
}
