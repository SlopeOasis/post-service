package com.slopeoasis.post.clerk;

/**
 * Represents the payload extracted from a verified Clerk JWT token.
 */
public class ClerkTokenPayload {
    private final String usid;      // User ID from 'usid' claim
    private final String wallet;    // Wallet address from 'wallet' claim (may be null)

    public ClerkTokenPayload(String usid, String wallet) {
        this.usid = usid;
        this.wallet = wallet;
    }

    public String getUsid() {
        return usid;
    }

    public String getWallet() {
        return wallet;
    }
}
