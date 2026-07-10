package com.zhubao.zhuaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
class AppTest {

    @Resource
    private App app;


    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是黄大铸，我想让另一半更爱我，但我不知道该怎么做";
        App.LoveReport loveReport = app.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }
}