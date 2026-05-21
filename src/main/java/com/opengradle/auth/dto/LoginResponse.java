package com.opengradle.auth.dto;

import com.opengradle.auth.UserContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login success payload — token + user details.
 *
 * <p>Clients are expected to send the token back on every subsequent request
 * via the {@code token} request header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    /** Opaque token; client stores and replays this. */
    private String token;
    /** TTL of the token, in seconds. */
    private long expiresIn;
    /** User details returned for convenience (no extra round-trip needed). */
    private UserContext user;
}
