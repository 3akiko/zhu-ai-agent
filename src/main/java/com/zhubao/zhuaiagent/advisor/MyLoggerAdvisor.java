package com.zhubao.zhuaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AbstractMessage;
import reactor.core.publisher.Flux;

import java.util.Optional;

/**
 * 自定义日志 Advisor
 * 打印 info 级别日志、只输出单次用户提示词和 AI 回复的文本
 */
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor, StreamAdvisor {

	@Override
	public String getName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public int getOrder() {
		return 100;   // 放到最外层，before/after 都能看到完整加工后的内容
	}

	private ChatClientRequest before(ChatClientRequest request) {
		log.info("[AI Request] {}", request.prompt());
		return request;
	}

	private void observeAfter(ChatClientResponse resp) {
		String text = Optional.ofNullable(resp.chatResponse())
				.map(org.springframework.ai.chat.model.ChatResponse::getResult)
				.map(org.springframework.ai.chat.model.Generation::getOutput)
				.map(AbstractMessage::getText)
				.orElse("[empty]");
		log.info("[AI Response] {}", text);
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest req, CallAdvisorChain chain) {
		req = before(req);
		ChatClientResponse resp = chain.nextCall(req);
		observeAfter(resp);
		return resp;
	}

	@Override
	public Flux<ChatClientResponse> adviseStream(ChatClientRequest req, StreamAdvisorChain chain) {
		req = before(req);
		return new ChatClientMessageAggregator()
				.aggregateChatClientResponse(chain.nextStream(req), this::observeAfter);
	}
}
