package com.zhubao.zhuaiagent.rag;

import com.zhubao.zhuaiagent.advisor.MyLoggerAdvisor;
import com.zhubao.zhuaiagent.advisor.ReReadingAdvisor;
import com.zhubao.zhuaiagent.memory.FileBasedChatMemory;
import com.zhubao.zhuaiagent.tools.LocationWeatherMockTools;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ReAct 循环: getLocation → getWeather("杭州") → final answer
 */
@SpringBootTest
class LocationWeatherMockToolsTest {

    @Autowired
    private ChatClient chatClient;

    private static final String SYSTEM_PROMPT = """
            你是智能助手,严格遵循 ReAct 框架(思考→行动→观察)解决问题。
            每轮仅调用 1 个工具,调工具前先说明理由。
            """;

    @Autowired
    private LocationWeatherMockTools locationWeatherMockTools;


    /**
     * 集成测试: 走真实 DashScope + ToolCallAdvisor 循环
     * 前提: application.yml 里 dashscope api-key 配好
     */
    @Test
    void shouldCallGetLocationThenGetWeather_andContainResultInFinalAnswer() {
        String answer = chatClient.prompt()
                .user("我当前地址的天气怎么样?")
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, UUID.randomUUID().toString()))
                .call()
                .content();

        System.out.println("=== LLM 最终回答 ===");
        System.out.println(answer);

        assertThat(answer)
                .as("最终回答应包含城市和天气关键词")
                .contains("杭州")
                .containsAnyOf("晴", "28", "天气");

        // 注: 工具是否被调用、调用几次,靠 MyLoggerAdvisor 打日志最直观
        // 如果要程序化断言,需要自定义 Advisor 收集 ToolCall 次数(下面附方案)
    }

    /**
     * 纯工具单元测,不跑 LLM —— 验证 a/b 返回值本身
     */
    @Test
    void getLocation_shouldReturnHangzhou() {
        String loc = locationWeatherMockTools.getLocation();
        assertThat(loc).isEqualTo("杭州");
    }

    @Test
    void getWeather_shouldReturnMockData() {
        String weather = locationWeatherMockTools.getWeather("杭州");
        assertThat(weather).contains("28°C").contains("湿度");
    }

    @Test
    void getWeather_unknownCity_shouldReturnFallback() {
        String weather = locationWeatherMockTools.getWeather("火星");
        assertThat(weather).contains("未知城市");
    }
}