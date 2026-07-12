package com.zhubao.zhuaiagent.advisor;

import com.google.common.collect.Lists;
import com.zhubao.zhuaiagent.constant.FileConstant;
import com.zhubao.zhuaiagent.entity.Reference;
import com.zhubao.zhuaiagent.entity.RagStreamEvent;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义流式Advisor：在LLM输出前，先发出 references 事件
 */
public class ReferenceEmitAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String RAG_REFERENCE_FLUX = "rag_reference_flux";

    private final int order;

    public  ReferenceEmitAdvisor() {
        order = 1;
    }

    public ReferenceEmitAdvisor(int order) {
        this.order = order;
    }

    @Override
    public String getName() {
        return "ReferenceEmitAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    // ==================== 同步非流式 adviseCall（完善对齐流式逻辑） ====================
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // 1. RAG 已经在前置 RetrievalAugmentationAdvisor.before() 完成，文档存在 request.context
        List<Document> docs = (List<Document>) request.context()
                .get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);

        // 先走完整链路拿到 LLM 原始返回
        ChatClientResponse originResponse = chain.nextCall(request);

        // 无知识库文档，直接原样返回
        if (docs == null || docs.isEmpty()) {
            return originResponse;
        }

        // 组装引用对象
        List<Reference> references = buildReferences(docs);
        RagStreamEvent refEvent = RagStreamEvent.references(references);

        // 把引用事件塞入响应元数据，上层同步接口可统一读取附件引用
        ChatResponse originChatResp = originResponse.chatResponse();
        ChatResponse newChatResp = ChatResponse.builder()
                .from(originChatResp)
                .metadata(FileConstant.RAG_EVENT, refEvent).build();


        // 透传原始完整上下文，不丢失链路参数
        return ChatClientResponse.builder()
                .context(originResponse.context())
                .chatResponse(newChatResp)
                .build();
    }


    // ---- 流式：先拼 references 事件，再走 LLM 内容流 ----
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        List<Document> docs = (List<Document>) request.context()
                .get(RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT);

        if (docs == null || docs.isEmpty()) {
            return chain.nextStream(request);
        }

        List<Reference> references = buildReferences(docs);
        Map<String, Object> map = new HashMap<>();
        map.put(FileConstant.RAG_EVENT, RagStreamEvent.references(references));
        // references 事件：包装成 ChatClientResponse，用 metadata 携带 RagStreamEvent
        ChatClientResponse refResp = ChatClientResponse.builder()
                .chatResponse(new ChatResponse(Lists.newArrayList(), ChatResponseMetadata.builder().metadata(map).build()))
                .build();
        Flux<ChatClientResponse> refFlux = Flux.just(refResp);
        Flux<ChatClientResponse> llmFlux = chain.nextStream(request);
        // 先引用，后内容
        return refFlux.concatWith(llmFlux);
    }

    private List<Reference> buildReferences(List<Document> docs) {
        AtomicInteger idx = new AtomicInteger(1);
        return docs.stream().map(doc -> {
            Map<String, Object> meta = doc.getMetadata();
            String text = doc.getText();
            return new Reference(
                    idx.getAndIncrement(),
                    (String) meta.getOrDefault("filename", "未知文档"),
                    (Integer) meta.getOrDefault("page", null),
                    (Integer) meta.getOrDefault("chunkIndex", -1),
                    text.substring(0, Math.min(120, text.length())),
                    (String) meta.getOrDefault("sourceUrl", null)
            );
        }).toList();
    }
}