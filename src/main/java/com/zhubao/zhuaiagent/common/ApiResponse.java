package com.zhubao.zhuaiagent.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<>(200, msg, data);
    }

    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<>(code, msg, null);
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(500, msg, null);
    }

    // ====== 流式事件专用方法 ======

    /**
     * 增量内容事件
     */
    public static <T> ApiResponse<T> delta(T data) {
        return new ApiResponse<>(200, "delta", data);
    }

    /**
     * 流结束事件
     */
    public static <T> ApiResponse<T> done() {
        return new ApiResponse<>(200, "done", null);
    }
}