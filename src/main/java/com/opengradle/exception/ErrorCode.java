package com.opengradle.exception;

/**
 * 网关统一的错误码定义。
 *
 * <p>取值与 HTTP 状态码对齐(401/403/500…),让调用方能用同一个整数判断结果,
 * 不必区分是 HTTP 层还是业务层的错误。
 */
public interface ErrorCode {
    int OK = 0;
    int INTERNAL_SERVER_ERROR = 500;
    int UNAUTHORIZED = 401;
    int FORBIDDEN = 403;
    int NOT_FOUND = 404;
    int BAD_REQUEST = 400;
    int TOO_MANY_REQUESTS = 429;
}
