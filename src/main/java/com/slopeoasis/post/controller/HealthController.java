package com.slopeoasis.post.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * Health check endpoint for debugging connectivity.
 */
@RestController
@RequestMapping("/healthpost")
public class HealthController {
    
    @Operation(summary = "Health check")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Service is healthy")
    })
    @GetMapping
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Post-service backend is running!!");
    }
}
