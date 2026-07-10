package com.zhubao.zhuaiagent.util;


import com.alibaba.cloud.ai.transformer.splitter.SentenceSplitter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.File;

public class splitTextTest {

    @Test
    void readPdfAndPrintRawText() throws Exception {
        // 方法1：使用绝对路径（你的本地路径）
        String absolutePath = "/Users/huangdazhu/学习之路/面试题目/Java 基础面试题速记通关版 _ 面试刷题 mianshiya.com.pdf";

        // 方法2：使用类路径资源（推荐，把PDF放到src/test/resources/pdf/下）
        // File pdfFile = ResourceUtils.getFile("classpath:pdf/java-interview.pdf");

        File pdfFile = new File(absolutePath);

        if (!pdfFile.exists()) {
            System.err.println("PDF文件不存在: " + absolutePath);
            return;
        }

        System.out.println("=== PDF文件信息 ===");
        System.out.println("文件路径: " + pdfFile.getAbsolutePath());
        System.out.println("文件大小: " + (pdfFile.length() / 1024) + " KB");

        String fullText;
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // 设置起始页和结束页（可选，加快测试速度）
//            stripper.setStartPage(1);
//            stripper.setEndPage(3); // 只读取前3页，避免输出太多
            PDDocumentOutline documentCatalog = document.getDocumentCatalog().getDocumentOutline();

            fullText = stripper.getText(document);
        }
        System.out.println(fullText);
        SentenceSplitter sentenceSplitter = new SentenceSplitter();
    }
}
