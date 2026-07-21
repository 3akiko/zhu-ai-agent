package com.zhubao.zhuaiagent.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.zhubao.zhuaiagent.advisor.MyLoggerAdvisor;
import com.zhubao.zhuaiagent.advisor.ReReadingAdvisor;
import com.zhubao.zhuaiagent.tools.LocationWeatherMockTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            你是智能助手,严格遵循 ReAct 框架(思考→行动→观察)解决问题。
            每轮仅调用 1 个工具,调工具前先说明理由。
            """;

    @Bean
    public ChatClient chatClient(DashScopeChatModel dashScopeChatModel,
                                 ChatMemory chatMemory,
                                 LocationWeatherMockTools locationWeatherMockTools) {
        return ChatClient.builder(dashScopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultTools(locationWeatherMockTools)   // ← 挂工具,a+b 都注册
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new MyLoggerAdvisor(),
                        new ReReadingAdvisor()
                )
                .build();
    }
}