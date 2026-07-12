package com.zhubao.zhuaiagent.entity;

/**
 * RAG 流式事件
 */
public record RagStreamEvent(RagStreamEventType type, Object data) {
    // 可选：提供便捷的工厂方法
    public static RagStreamEvent references(Object data) {
        return new RagStreamEvent(RagStreamEventType.REFERENCES, data);
    }

    public static RagStreamEvent delta(Object data) {
        return new RagStreamEvent(RagStreamEventType.DELTA, data);
    }

    public static RagStreamEvent done() {
        return new RagStreamEvent(RagStreamEventType.DONE, null);
    }
}