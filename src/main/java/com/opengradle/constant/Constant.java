package com.opengradle.constant;

/**
 * Common string constants used across filters and controllers.
 */
public interface Constant {

    /** HTTP header name for the auth token. */
    String TOKEN_HEADER = "token";

    /** HTTP header name for the tenant code (multi-tenant routing). */
    String HEADER_TENANT_CODE = "tenantCode";

    /** Forwarded-for header. */
    String X_FORWARDED_FOR = "x-forwarded-for";

    // ---------- Downstream identity headers injected by AuthFilter ----------

    /** Resolved userId injected for downstream services. */
    String HEADER_USER_ID = "x-user-id";

    /** Resolved username injected for downstream services. */
    String HEADER_USERNAME = "x-username";

    /** Resolved tenantCode injected for downstream services. */
    String HEADER_TENANT_CODE_INJECTED = "x-tenant-code";

    /** Boolean flag "true"/"false" — whether the user is a platform super-admin. */
    String HEADER_SUPER_ADMIN = "x-super-admin";
}
