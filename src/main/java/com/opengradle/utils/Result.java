package com.opengradle.utils;

import com.opengradle.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * Unified response envelope.
 * <pre>
 * { "code": 0, "msg": "ok", "data": ... }
 * </pre>
 */
@Data
public class Result<T> implements Serializable {

    private int code = ErrorCode.OK;
    private String msg = "ok";
    private T data;

    public Result<T> ok(T data) {
        this.data = data;
        return this;
    }

    public Result<T> error(int code, String msg) {
        this.code = code;
        this.msg = msg;
        return this;
    }

    public boolean success() {
        return this.code == ErrorCode.OK;
    }

    public static <T> Result<T> of(T data) {
        return new Result<T>().ok(data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<T>().error(code, msg);
    }
}
