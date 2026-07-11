package com.zhubao.zhuaiagent.app;


import com.zhubao.zhuaiagent.advisor.MyLoggerAdvisor;
import com.zhubao.zhuaiagent.memory.FileBasedChatMemory;
import com.zhubao.zhuaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
@Slf4j
public class ZhuaiApp {

    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是一位资深的 Java 面试辅导专家，精通 Java 核心技术、并发编程、JVM、Spring 框架等。
            请根据用户的问题，给出详细、准确的解答，并结合实际代码示例和面试经验进行说明。
            如果用户询问具体知识点，请先给出核心概念，再深入讲解原理和常见面试题。
            回答要清晰、有条理，适当使用代码片段和比喻。
            """;

    /**
     * 初始化 ChatClient
     *
     * @param dashScopeChatModel 注入的 DashScope ChatModel
     */
    public ZhuaiApp(ChatModel dashScopeChatModel) {
        // 初始化基于文件的对话记忆（也可切换到内存模式）
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 内存模式（可选）：
        // MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
        //         .chatMemoryRepository(new InMemoryChatMemoryRepository())
        //         .maxMessages(20)
        //         .build();

        chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor（打印请求和响应）
                        new MyLoggerAdvisor()
                        // 可选的 ReReading Advisor（二次阅读增强）
                        // new ReReadingAdvisor()
                )
                .build();
    }

    // ==================== 基础对话 ====================

    /**
     * 基础对话（支持多轮记忆）
     */
    public String doChat(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("doChat response: {}", content);
        return content;
    }

    /**
     * 基础对话（流式 SSE）
     */
    public Flux<String> doChatStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .content();
    }

    // ==================== 结构化输出 ====================

    /**
     * 生成面试题解答报告（结构化输出）
     */
    public InterviewReport doChatWithReport(String message, String chatId) {
        InterviewReport report = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + """
                        每次回答后，请以 JSON 格式输出面试题解答报告，包含：
                        - title: 问题标题
                        - keyPoints: 核心要点列表
                        - difficulty: 难度等级（简单/中等/困难）
                        """)
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(InterviewReport.class);
        log.info("InterviewReport: {}", report);
        return report;
    }

    // 结构化输出记录
    public record InterviewReport(String title, List<String> keyPoints, String difficulty) {}

    // ==================== RAG 混合检索对话 ====================

    @Resource
    private VectorStore hybridVectorStore;  // 你的 ObservedHybridVectorStore

    @Resource
    private QueryRewriter queryRewriter;

    /**
     * 带 RAG 知识库的对话（非流式）
     */
    public String doChatWithRag(String message, String chatId) {
        // 查询重写，提高检索质量
        String rewritten = queryRewriter.doQueryRewrite(message);
        log.debug("原始查询: {}, 重写后: {}", message, rewritten);

        ChatResponse response = chatClient
                .prompt()
                .user(rewritten)
                .advisors(spec -> {
                    spec.param(ChatMemory.CONVERSATION_ID, chatId);
                })
                .advisors(new MyLoggerAdvisor())
                .advisors(QuestionAnswerAdvisor.builder(hybridVectorStore).build())
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("doChatWithRag response: {}", content);
        return content;
    }

    /**
     * 带 RAG 知识库的对话（流式 SSE）
     */
    public Flux<String> doChatWithRagStream(String message, String chatId) {
        String rewritten = queryRewriter.doQueryRewrite(message);
        log.debug("原始查询: {}, 重写后: {}", message, rewritten);

        return chatClient
                .prompt()
                .user(rewritten)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new MyLoggerAdvisor())
                .advisors(QuestionAnswerAdvisor.builder(hybridVectorStore).build())
                .stream()
                .content();
    }

    // ==================== 预留工具调用（暂不实现） ====================

    // 后续可在此添加 tools 和 MCP 相关方法
}