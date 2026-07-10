package com.zhubao.zhuaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring AI 框架调用 AI 大模型（阿里）
 */
//@Component
public class SpringAiAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashScopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage assistantMessage = dashScopeChatModel.call(new Prompt("你好，我是大铸，能给我从音乐风格，人员构成等方面介绍一下Mujica这只乐队吗"))
                .getResult()
                .getOutput();
        System.out.println(assistantMessage.getText());
    }


}
