package com.zhubao.zhuaiagent.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PdfLinkExtractorTest {

    @Resource
    PdfLinkExtractor linkExtractor;

    @Resource
    PdfParserUtil pdfParserUtil;


    @Test
    void testExtractAndSplit() throws Exception {
        // 1. 加载 PDF
        String path = "/Users/huangdazhu/学习之路/面试题目/Java 基础面试题速记通关版 _ 面试刷题 mianshiya.com.pdf";
        File pdfFile = new File(path);
        assertTrue(pdfFile.exists(), "PDF 文件不存在");

        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            // 2. 提取全文
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            // 3. 提取锚点
            List<PdfLinkExtractor.LinkAnchor> anchors = linkExtractor.extractLinkAnchors(document);
            System.out.println("找到 " + anchors.size() + " 个超链接锚点");

            // 打印所有锚点文本（用于调试）
            anchors.forEach(a -> System.out.println("锚点: [" + a.page() + "] " + a.text()));

            // 4. 按锚点切分题目
            List<String> questions = linkExtractor.splitByLinkAnchors(fullText, anchors);
            System.out.println("\n识别出 " + questions.size() + " 道题目：");
            for (int i = 0; i < questions.size(); i++) {
                String title = questions.get(i).split("\n")[0];
                System.out.println("第" + (i+1) + "题: " + title.substring(0, Math.min(40, title.length())));
                System.out.println("=======原文===========");
                System.out.println(questions.get(i));
            }

            // 5. 断言至少识别出 3 道题（你的样本至少有 3 道）
            assertTrue(questions.size() >= 3, "应该至少识别出 3 道题目");
        }
    }

    @Test
    public void testSplitByQuestions() throws IOException {
        String path = "/Users/huangdazhu/学习之路/面试题目/Java 基础面试题速记通关版 _ 面试刷题 mianshiya.com.pdf";
 //       String path = "/Users/huangdazhu/Downloads/测试数据/最新面试文档.pdf";
        File pdfFile = new File(path);
        assertTrue(pdfFile.exists(), "PDF 文件不存在");

        // 5. 使用 PDFBox 提取文本


        List<PdfParserUtil.QuestionChunk> questions;
        try (PDDocument document = Loader.loadPDF(new File(path))) {
            questions = pdfParserUtil.splitByQuestions(document);
        }

        // 6、在 uploadPdf 方法中，修改分块循环
        List<String> allChunks = new ArrayList<>();
        List<String> allSourceUrls = new ArrayList<>(); // 记录每个 chunk 对应的 sourceUrl

        for (PdfParserUtil.QuestionChunk question : questions) {
            //切分，加上url
            List<String> subChunks = pdfParserUtil.splitQuestionIntoChunks(question.getText(), 1024, 128);
            for (String chunk : subChunks) {
                allChunks.add(chunk);
                allSourceUrls.add(question.getSourceUrl()); // 每个子 chunk 继承题目的 sourceUrl
            }
        }

        System.out.println(JsonUtils.toJson(allChunks));
        System.out.println(JsonUtils.toJson(allSourceUrls));

    }



}