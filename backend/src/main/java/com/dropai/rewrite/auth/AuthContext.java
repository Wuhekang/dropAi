package com.dropai.rewrite.auth;

public final class AuthContext {
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new IllegalStateException("请先登录");
        }
        return userId;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
