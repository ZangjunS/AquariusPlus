package com.ds.common.model;

import com.ds.common.constant.CommonConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "统一响应体")
public class Result<T> implements Serializable {

    @Schema(description = "业务码，0 表示成功")
    private int code;

    @Schema(description = "提示消息")
    private String message;

    @Schema(description = "数据")
    private T data;

    @Schema(description = "服务器时间戳")
    private long timestamp = System.currentTimeMillis();

    public Result() {}

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> ok() {
        return new Result<>(CommonConstants.CODE_SUCCESS, "success", null);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> ok(T data) {
        return new Result<>(CommonConstants.CODE_SUCCESS, "success", data);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(CommonConstants.CODE_SUCCESS, message, data);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail(String message) {
        return new Result<>(CommonConstants.CODE_FAIL, message, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> unauthorized() {
        return fail(CommonConstants.CODE_UNAUTHORIZED, "未认证或令牌无效");
    }

    @SuppressWarnings("unchecked")
    public static <T> Result<T> forbidden() {
        return fail(CommonConstants.CODE_FORBIDDEN, "无权限访问");
    }
}
