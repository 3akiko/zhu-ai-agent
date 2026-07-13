package com.zhubao.zhuaiagent.rag;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

public interface QueryRewriter {
    // 原有单参数方法（兼容旧调用）
    String doQueryRewrite(String userText);
    // 新增：带对话历史的重载方法（本次RAG必须用）
    String doQueryRewrite(String userText, List<Message> history);
}