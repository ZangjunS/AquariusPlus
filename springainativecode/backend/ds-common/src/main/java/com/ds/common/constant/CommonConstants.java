package com.ds.common.constant;

public final class CommonConstants {

    private CommonConstants() {}

    /** 网关透传的鉴权请求头 */
    public static final String HEADER_TOKEN = "Authorization";
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";

    /** 统一返回码 */
    public static final int CODE_SUCCESS = 0;
    public static final int CODE_FAIL = 500;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
}
