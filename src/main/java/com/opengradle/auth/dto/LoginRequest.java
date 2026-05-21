package com.opengradle.auth.dto;

import lombok.Data;

/**
 * Login request payload — kept deliberately small. Validation is performed in
 * the service layer (no Bean Validation pulled in to keep deps minimal).
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
