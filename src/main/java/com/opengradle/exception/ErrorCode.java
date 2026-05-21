package com.opengradle.exception;

/**
 * Standard error codes returned by the gateway.
 *
 * <p>HTTP-aligned codes (401/403/500) are used in the JSON body so that
 * callers can branch on a single integer regardless of transport semantics.
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
