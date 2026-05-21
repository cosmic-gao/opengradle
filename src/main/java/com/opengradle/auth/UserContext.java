package com.opengradle.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Authenticated user context, cached in Redis under {@code TOKENS:&lt;token&gt;}.
 *
 * <p>This is the minimum payload the gateway needs to identify a request:
 * the user identity and tenant scope. Downstream services receive this
 * information via injected request headers (see {@code AuthFilter}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Stable user identifier (snowflake / DB id / UUID). */
    private Long userId;

    /** Display name / login name. */
    private String username;

    /** Tenant scope; null for non multi-tenant deployments. */
    private Long tenantCode;

    /** True for platform super-admins (cross-tenant access). */
    private boolean superAdmin;
}
