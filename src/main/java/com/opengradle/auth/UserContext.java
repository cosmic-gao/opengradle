package com.opengradle.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 已认证用户上下文,作为 token 对应的值缓存在 {@link TokenStore} 中。
 *
 * <p>这是网关识别一个请求所需的最小载荷:用户身份 + 租户范围。下游服务
 * 通过 AuthFilter 注入的请求头拿到这些信息(无需再校验 token)。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 稳定的用户唯一标识(雪花 id / DB 主键 / UUID 任选)。 */
    private Long userId;

    /** 展示名 / 登录名。 */
    private String username;

    /** 租户编码;非多租户部署可为 null。 */
    private Long tenantCode;

    /** 是否为平台超级管理员(可跨租户访问)。 */
    private boolean superAdmin;
}
