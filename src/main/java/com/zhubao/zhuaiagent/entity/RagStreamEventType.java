package com.zhubao.zhuaiagent.entity;

/**
 * RAG 流式事件类型
 */
public enum RagStreamEventType {
    REFERENCES("references"),
    DELTA("delta"),
    DONE("done");

    private final String value;

    RagStreamEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RagStreamEventType fromValue(String value) {
        for (RagStreamEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RagStreamEventType: " + value);
    }
}