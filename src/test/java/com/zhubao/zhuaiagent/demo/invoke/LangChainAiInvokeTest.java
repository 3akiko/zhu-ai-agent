package com.zhubao.zhuaiagent.demo.invoke;

import dev.langchain4j.community.model.dashscope.QwenChatModel;

public class LangChainAiInvokeTest {

    public static void main(String[] args) {
        QwenChatModel qwenChatModel = QwenChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-max")
                .enableSearch(true)
                .build();
        String answer = qwenChatModel.chat("你好，你是谁，我很喜欢邦多利的Mujica，你知道这支乐队吗？");
        System.out.println(answer);
    }
}
