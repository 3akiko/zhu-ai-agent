package com.zhubao.zhuaiagent.app;


import com.zhubao.zhuaiagent.advisor.MyLoggerAdvisor;
import com.zhubao.zhuaiagent.advisor.ReferenceEmitAdvisor;
import com.zhubao.zhuaiagent.constant.FileConstant;
import com.zhubao.zhuaiagent.entity.RagResponse;
import com.zhubao.zhuaiagent.entity.RagStreamEvent;
import com.zhubao.zhuaiagent.entity.RagStreamEventType;
import com.zhubao.zhuaiagent.entity.Reference;
import com.zhubao.zhuaiagent.memory.FileBasedChatMemory;
import com.zhubao.zhuaiagent.rag.impl.LLMQueryRewriter;
import com.zhubao.zhuaiagent.util.JsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class ZhuaiApp {

    private final ChatClient chatClient;

    private final RetrievalAugmentationAdvisor ragAdvisor;

    private final ReferenceEmitAdvisor refAdvisor;

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
    public ZhuaiApp(ChatModel dashScopeChatModel, VectorStore hybridVectorStore) {
        // 初始化基于文件的对话记忆（也可切换到内存模式）
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 内存模式（可选）：
        // MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
        //         .chatMemoryRepository(new InMemoryChatMemoryRepository())
        //         .maxMessages(20)
        //         .build();

        ragAdvisor = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(new VectorStoreDocumentRetriever(hybridVectorStore, 0.6, 3, null))
                .queryTransformers(query -> {
                    String rewritten = llmQueryRewriter.doQueryRewrite(query.text(), query.history());
                    return Query.builder().text(rewritten)
                            .context(query.context())
                            .history(query.history()).build();
                })
                .build();

        // 引用前置 Advisor（order 要在 ragAdvisor 之后，ragAdvisor 默认 order 0）
        refAdvisor = new ReferenceEmitAdvisor(1);

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
    private LLMQueryRewriter llmQueryRewriter;

    /**
     * 带 RAG 知识库的对话（非流式）
     */
    public RagResponse doChatWithRag(String message, String chatId) {
        ChatResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT + "\n\n请根据参考资料回答问题。")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(ragAdvisor, refAdvisor)
                .call()
                .chatResponse();

        // 从 metadata 中提取 references
        List<Reference> references = Collections.emptyList();
        Object ragEvent = response.getMetadata().get(FileConstant.RAG_EVENT);
        if (ragEvent instanceof RagStreamEvent e && RagStreamEventType.REFERENCES.equals(e.type())) {
            references = (List<Reference>) e.data();
        }

        // 获取答案文本
        String content = response.getResult().getOutput().getText();
        RagResponse ragResponse = new RagResponse(content, references);
        log.info("doChatWithRag ragResponse: {}", JsonUtils.toJson(ragResponse));
        return ragResponse;
    }

    /**
     * 带 RAG 知识库的对话（流式 SSE）
     */
    public Flux<RagStreamEvent> doChatWithRagStream(String message, String chatId) {
        // RAG Advisor：用你的混合检索 VectorStore,、

        return chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "\n\n请根据参考资料回答问题。")
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(ragAdvisor, refAdvisor)
                .stream()
                .chatClientResponse().flatMap(resp -> {
                    // 判断是否是引用事件（来自 ReferenceEmitAdvisor）
                    Object ragEvent = resp.chatResponse().getMetadata().get(FileConstant.RAG_EVENT);
                    if (ragEvent instanceof RagStreamEvent) {
                        log.info("doChatWithRag, rag event resp: {}", JsonUtils.toJson(ragEvent));
                        return Flux.just((RagStreamEvent) ragEvent);
                    }

                    // LLM 正文 delta
                    log.info("doChatWithRag response: {}", JsonUtils.toJson(resp));
                    String text = resp.chatResponse().getResult().getOutput().getText();
                    String delta = text != null ? text : "";
                    return Flux.just(RagStreamEvent.delta( delta));
                })
                .concatWithValues(RagStreamEvent.done());
    }

    // ==================== 预留工具调用（暂不实现） ====================

    // 后续可在此添加 tools 和 MCP 相关方法
}