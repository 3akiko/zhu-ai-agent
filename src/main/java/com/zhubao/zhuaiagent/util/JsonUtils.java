package com.zhubao.zhuaiagent.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        // 可选：关闭时间戳格式（输出 ISO 字符串而非数字）
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ==================== 对象转 JSON 字符串 ====================

    /**
     * 对象转 JSON 字符串，失败时抛出异常
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("对象转 JSON 失败: {}", e.getMessage());
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /**
     * 对象转 JSON 字符串，失败时返回 null
     */
    public static String toJsonQuietly(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("对象转 JSON 失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    // ==================== JSON 字符串转对象 ====================

    /**
     * JSON 字符串转对象，失败时抛出异常
     */
    public static <T> T jsonToObject(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 转对象失败: {}", e.getMessage());
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    /**
     * JSON 字符串转对象，失败时返回 null
     */
    public static <T> T jsonToObjectQuietly(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("JSON 转对象失败，返回 null: {}", e.getMessage());
            return null;
        }
    }

    // ==================== JSON 字符串转 TypeReference（泛型） ====================

    /**
     * JSON 字符串转 TypeReference，失败时抛出异常
     */
    public static <T> T jsonToTypeRef(String json, TypeReference<T> typeRef) {
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.error("JSON 转 TypeReference 失败: {}", e.getMessage());
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    /**
     * JSON 字符串转 TypeReference，失败时返回 null
     */
    public static <T> T jsonToTypeRefQuietly(String json, TypeReference<T> typeRef) {
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (JsonProcessingException e) {
            log.warn("JSON 转 TypeReference 失败，返回 null: {}", e.getMessage());
            return null;
        }
    }
}