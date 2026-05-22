package com.opengradle.constant;

/**
 * 过滤器和控制器共用的字符串常量集中处。
 */
public interface Constant {

    /** 鉴权 token 的 HTTP 头名称。 */
    String TOKEN_HEADER = "token";

    /** 租户编码 HTTP 头(多租户路由用)。 */
    String HEADER_TENANT_CODE = "tenantCode";

    /** 客户端真实 IP 转发头。 */
    String X_FORWARDED_FOR = "x-forwarded-for";

    // ---------- 由 AuthFilter 写入、给下游服务读取的身份头 ----------

    /** 解析后的 userId,注入给下游服务。 */
    String HEADER_USER_ID = "x-user-id";

    /** 解析后的 username,注入给下游服务。 */
    String HEADER_USERNAME = "x-username";

    /** 解析后的 tenantCode,注入给下游服务。 */
    String HEADER_TENANT_CODE_INJECTED = "x-tenant-code";

    /** 字符串 "true"/"false" —— 是否为平台超级管理员。 */
    String HEADER_SUPER_ADMIN = "x-super-admin";
}
