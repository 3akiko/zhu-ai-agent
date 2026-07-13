package com.zhubao.zhuaiagent.rag.impl;

import com.zhubao.zhuaiagent.rag.QueryRewriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询重写：将用户问题改写为更适合检索的表述
 */
@Component
@Slf4j
public class LLMQueryRewriter implements QueryRewriter {

    private final ChatClient chatClient;

    private static final String REWRITE_PROMPT = """
            你是一个查询优化助手。请将用户的问题改写为更清晰、更利于知识库检索的表述。
            要求：
            1. 保留核心关键词和技术术语
            2. 去除口语化表达，使问题更精确
            3. 如果问题涉及多个知识点，拆分或合并为单一明确的查询
            4. 直接输出改写后的查询，不要添加任何解释
            5. 如果涉及到历史信息，则总结历史信息进行重写
            """;

    public LLMQueryRewriter(ChatModel dashScopeChatModel) {
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(REWRITE_PROMPT)
                .build();
    }

    @Override
    public String doQueryRewrite(String userText) {
        return doQueryRewrite(userText, List.of());
    }

    @Override
    public String doQueryRewrite(String userText, List<Message> history) {
        StringBuilder historyPrompt = new StringBuilder();
        if (!history.isEmpty()) {
            historyPrompt.append("【历史对话上下文】\n");
            for (Message msg : history) {
                historyPrompt.append(msg.getMessageType()).append("：").append(msg.getText()).append("\n");
            }
        }
        String fullPrompt = historyPrompt + """
                用户当前问题：%s
                任务：结合历史对话，把用户模糊问题扩写为精准、独立、可检索知识库的查询语句，只输出改写后的句子，不要多余解释。
                """.formatted(userText);

        // 调用小模型做查询改写
        String content = chatClient.prompt()
                .user(fullPrompt)
                .call()
                .content();
        log.info("查询重写: {} -> {}", fullPrompt, content);
        return content;
    }

}