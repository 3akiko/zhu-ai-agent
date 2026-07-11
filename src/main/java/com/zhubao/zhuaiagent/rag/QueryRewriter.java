package com.zhubao.zhuaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 查询重写：将用户问题改写为更适合检索的表述
 */
@Component
@Slf4j
public class QueryRewriter {

    private final ChatClient chatClient;

    private static final String REWRITE_PROMPT = """
            你是一个查询优化助手。请将用户的问题改写为更清晰、更利于知识库检索的表述。
            要求：
            1. 保留核心关键词和技术术语
            2. 去除口语化表达，使问题更精确
            3. 如果问题涉及多个知识点，拆分或合并为单一明确的查询
            4. 直接输出改写后的查询，不要添加任何解释
            """;

    public QueryRewriter(ChatModel dashScopeChatModel) {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(REWRITE_PROMPT)
                .build();
    }

    public String doQueryRewrite(String query) {
        String rewritten = chatClient.prompt().user(query).call().content();
        log.info("查询重写: {} -> {}", query, rewritten);
        return rewritten;
    }
}