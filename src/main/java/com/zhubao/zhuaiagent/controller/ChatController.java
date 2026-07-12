package com.zhubao.zhuaiagent.controller;

import com.zhubao.zhuaiagent.app.ZhuaiApp;
import com.zhubao.zhuaiagent.common.ApiResponse;
import com.zhubao.zhuaiagent.entity.RagResponse;
import com.zhubao.zhuaiagent.entity.RagStreamEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ZhuaiApp zhuaiApp;

    // ==================== 基础对话（非流式） ====================

    @PostMapping("/basic")
    public ApiResponse<String> basicChat(@RequestBody ChatRequest request) {
        String result = zhuaiApp.doChat(request.message(), request.chatId());
        return ApiResponse.success(result);
    }

    // ==================== 基础对话（流式） ====================

    @PostMapping(value = "/basic/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ApiResponse<String>> basicChatStream(@RequestBody ChatRequest request) {
        return zhuaiApp.doChatStream(request.message(), request.chatId())
                .map(ApiResponse::delta)               // 每个 chunk 包装为 delta 事件
                .concatWithValues(ApiResponse.done());  // 最后发送 done 事件
    }

    // ==================== 结构化输出（非流式） ====================

    @PostMapping("/report")
    public ApiResponse<ZhuaiApp.InterviewReport> generateReport(@RequestBody ChatRequest request) {
        ZhuaiApp.InterviewReport report = zhuaiApp.doChatWithReport(request.message(), request.chatId());
        return ApiResponse.success(report);
    }

    // ==================== RAG 对话（非流式） ====================

    @PostMapping("/rag")
    public ApiResponse<RagResponse> ragChat(@RequestBody ChatRequest request) {
        RagResponse result = zhuaiApp.doChatWithRag(request.message(), request.chatId());
        return ApiResponse.success(result);
    }

    // ==================== RAG 对话（流式） ====================

    // ==================== 流式 RAG（SSE，带引用） ====================
    @PostMapping(value = "/rag/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<RagStreamEvent>> ragChatStream(@RequestBody ChatRequest request) {
        return zhuaiApp.doChatWithRagStream(request.message(), request.chatId())
                .map(event -> ServerSentEvent.<RagStreamEvent>builder()
                        .event(event.type().getValue())   // "references" / "delta" / "done"
                        .data(event)
                        .build());
    }

    // ==================== 请求体 DTO ====================

    public record ChatRequest(String message, String chatId) {}
}