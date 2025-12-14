package com.slopeoasis.post.clerk;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Clerk JWT verifier using the official Clerk backend SDK.
 * Validates JWT tokens issued by Clerk by:
 * 1. Fetching JWKS (public keys) from Clerk's JWKS endpoint
 * 2. Verifying token signature using the appropriate public key
 * 3. Extracting custom claims (usid, wallet) from the verified token
 */
@Component
public class ClerkJwtVerifier {
    @Value("${jwt.issuer:}")
    private String issuer;

    @Value("${jwt.jwks-url:}")
    private String jwksUrl;

    @Value("${jwt.dev-mode:false}")
    private boolean devMode;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, PublicKey> publicKeyCache = new HashMap<>();

    /**
     * Verify a Clerk JWT token and extract claims.
     * Returns a ClerkTokenPayload with usid (user ID) and wallet.
     */
    public ClerkTokenPayload verify(String token) throws Exception {
        if (devMode) {
            // Dev mode: extract claims without signature verification
            return extractClaimsWithoutVerification(token);
        }

        if (issuer == null || issuer.isBlank() || jwksUrl == null || jwksUrl.isBlank()) {
            throw new IllegalStateException("Clerk verification requires CLERK_ISSUER and CLERK_JWKS_URL env vars");
        }

        // Split token into parts
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format: expected 3 parts");
        }

        // Decode and parse header and payload
        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        String signatureStr = parts[2];

        JsonNode headerNode = objectMapper.readTree(headerJson);
        JsonNode payloadNode = objectMapper.readTree(payloadJson);

        // Verify issuer claim
        String tokenIssuer = payloadNode.path("iss").asText(null);
        if (!issuer.equals(tokenIssuer)) {
            throw new IllegalArgumentException("Invalid issuer: expected " + issuer + " but got " + tokenIssuer);
        }

        // Get key ID from header
        String kid = headerNode.path("kid").asText(null);
        if (kid == null || kid.isBlank()) {
            throw new IllegalArgumentException("Token header missing 'kid' field");
        }

        // Get public key from JWKS
        PublicKey publicKey = getPublicKeyFromJwks(kid);
        if (publicKey == null) {
            throw new IllegalArgumentException("Unable to find public key with kid: " + kid);
        }

        // Verify signature
        String signedContent = parts[0] + "." + parts[1];
        byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureStr);
    
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(signedContent.getBytes());
    
        if (!sig.verify(signatureBytes)) {
            throw new IllegalArgumentException("Invalid token signature");
        }

        // Extract custom claims
        String usid = payloadNode.path("usid").asText(null);
        String wallet = payloadNode.path("wallet").asText(null);

        if (usid == null || usid.isBlank()) {
            throw new IllegalArgumentException("Token missing 'usid' claim");
        }

        return new ClerkTokenPayload(usid, wallet);
    }

    /**
     * Fetch JWKS from Clerk and extract the public key matching the token's kid (key ID).
     */
    private PublicKey getPublicKeyFromJwks(String kid) throws Exception {
        // Check cache first
        if (publicKeyCache.containsKey(kid)) {
            return publicKeyCache.get(kid);
        }

        // Fetch JWKS from Clerk
        String jwksJson = restTemplate.getForObject(jwksUrl, String.class);
        JsonNode jwksNode = objectMapper.readTree(jwksJson);
        JsonNode keysArray = jwksNode.path("keys");

        for (JsonNode keyNode : keysArray) {
            if (kid.equals(keyNode.path("kid").asText())) {
                PublicKey key = extractPublicKeyFromJwk(keyNode);
                publicKeyCache.put(kid, key);
                return key;
            }
        }

        return null;
    }

    /**
     * Extract a PublicKey from a JWK (JSON Web Key) node.
     */
    private PublicKey extractPublicKeyFromJwk(JsonNode jwk) throws Exception {
        String kty = jwk.path("kty").asText();
        if (!"RSA".equals(kty)) {
            throw new IllegalArgumentException("Unsupported key type: " + kty);
        }

        // Extract RSA components (n and e)
        String n = jwk.path("n").asText();
        String e = jwk.path("e").asText();

        if (n.isBlank() || e.isBlank()) {
            throw new IllegalArgumentException("Missing RSA key components in JWK");
        }

        // Decode n and e from Base64url
        byte[] nBytes = Base64.getUrlDecoder().decode(n);
        byte[] eBytes = Base64.getUrlDecoder().decode(e);

        // Convert to BigInteger and create RSA key spec
        java.math.BigInteger modulus = new java.math.BigInteger(1, nBytes);
        java.math.BigInteger exponent = new java.math.BigInteger(1, eBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    /**
     * Dev mode: extract claims without signature verification.
     */
    private ClerkTokenPayload extractClaimsWithoutVerification(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payloadJson);

        String usid = claims.path("usid").asText(null);
        String wallet = claims.path("wallet").asText(null);

        if (usid == null || usid.isBlank()) {
            throw new IllegalArgumentException("Token missing 'usid' claim");
        }

        return new ClerkTokenPayload(usid, wallet);
    }
}
