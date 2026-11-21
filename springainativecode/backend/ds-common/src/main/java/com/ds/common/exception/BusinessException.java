package com.ds.common.exception;

import com.ds.common.constant.CommonConstants;
import lombok.Getter;

/**
 * 业务异常
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        this(CommonConstants.CODE_FAIL, message);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException unauthorized() {
        return new BusinessException(CommonConstants.CODE_UNAUTHORIZED, "未认证或令牌无效");
    }

    public static BusinessException forbidden() {
        return new BusinessException(CommonConstants.CODE_FORBIDDEN, "无权限访问");
    }

    public static BusinessException notFound(String msg) {
        return new BusinessException(CommonConstants.CODE_NOT_FOUND, msg);
    }

    /** 快捷构造：业务失败 */
    public static BusinessException fail(String msg) {
        return new BusinessException(msg);
    }
}
