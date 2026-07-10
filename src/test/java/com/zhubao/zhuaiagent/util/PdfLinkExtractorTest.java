package com.zhubao.zhuaiagent.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfLinkExtractorTest {

    PdfLinkExtractor linkExtractor = new PdfLinkExtractor();

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

}