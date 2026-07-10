package com.zhubao.zhuaiagent.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class PdfParserUtilTest {

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

            fullText = stripper.getText(document);
        }
        System.out.println("\n=== 提取的原始文本===");
        System.out.println(fullText);

        System.out.println("\n=== 提取的原始文本（前2000字符）===");
        System.out.println(fullText.substring(0, Math.min(2000, fullText.length())));

        System.out.println("\n=== 文本统计信息 ===");
        System.out.println("总长度: " + fullText.length() + " 字符");
        System.out.println("行数: " + fullText.split("\n").length);
        System.out.println("空行数: " + (fullText.split("\n").length - fullText.replace("\n", "").length()));

        // 分析文本特征
        System.out.println("\n=== 文本特征分析 ===");
        analyzeTextFeatures(fullText);

        // 测试splitByQuestions方法
        System.out.println("\n=== 测试splitByQuestions方法 ===");

    }

    @Test
    void readPdfFullContent() throws Exception {
        // 这个测试会输出完整内容，适合保存到文件分析
        String absolutePath = "/Users/huangdazhu/学习之路/面试题目/Java 基础面试题速记通关版 _ 面试刷题 mianshiya.com.pdf";
        File pdfFile = new File(absolutePath);

        if (!pdfFile.exists()) {
            System.err.println("PDF文件不存在: " + absolutePath);
            return;
        }

        String fullText;
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true); // 按位置排序，对双栏PDF有帮助
            fullText = stripper.getText(document);
        }

        // 保存到文件方便查看
        Path outputPath = Paths.get("pdf-extracted-text.txt");
        Files.write(outputPath, fullText.getBytes());
        System.out.println("完整文本已保存到: " + outputPath.toAbsolutePath());

        // 同时打印前5000字符到控制台
        System.out.println("\n=== 前5000字符预览 ===");
        System.out.println(fullText.substring(0, Math.min(5000, fullText.length())));
    }

    @Test
    void analyzeSpecificPatterns() throws Exception {
        // 专门分析特定模式
        String absolutePath = "/Users/huangdazhu/学习之路/面试题目/Java 基础面试题速记通关版 _ 面试刷题 mianshiya.com.pdf";
        File pdfFile = new File(absolutePath);

        if (!pdfFile.exists()) {
            System.err.println("PDF文件不存在: " + absolutePath);
            return;
        }

        String fullText;
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(5); // 只分析前5页
            fullText = stripper.getText(document);
        }

        System.out.println("=== 特定模式分析 ===");

        // 1. 查找所有可能的题目开头模式
        System.out.println("\n1. 查找数字序号模式:");
        java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("(?m)^\\d+[\\.、\\)]");
        java.util.regex.Matcher numMatcher = numPattern.matcher(fullText);
        int numCount = 0;
        while (numMatcher.find() && numCount < 10) {
            System.out.println("  找到: " + numMatcher.group() + " 位置: " + numMatcher.start());
            numCount++;
        }
        if (numCount == 0) System.out.println("  未找到数字序号模式");

        // 2. 查找【】模式
        System.out.println("\n2. 查找【】模式:");
        java.util.regex.Pattern bracketPattern = java.util.regex.Pattern.compile("【.*?】");
        java.util.regex.Matcher bracketMatcher = bracketPattern.matcher(fullText);
        int bracketCount = 0;
        while (bracketMatcher.find() && bracketCount < 10) {
            System.out.println("  找到: " + bracketMatcher.group());
            bracketCount++;
        }
        if (bracketCount == 0) System.out.println("  未找到【】模式");

        // 3. 查找Q/A模式
        System.out.println("\n3. 查找Q/A模式:");
        java.util.regex.Pattern qaPattern = java.util.regex.Pattern.compile("(?m)^(Q|A|问|答)[:：]");
        java.util.regex.Matcher qaMatcher = qaPattern.matcher(fullText);
        int qaCount = 0;
        while (qaMatcher.find() && qaCount < 10) {
            System.out.println("  找到: " + qaMatcher.group() + " 位置: " + qaMatcher.start());
            qaCount++;
        }
        if (qaCount == 0) System.out.println("  未找到Q/A模式");

        // 4. 查找特殊符号模式
        System.out.println("\n4. 查找特殊符号模式:");
        java.util.regex.Pattern symbolPattern = java.util.regex.Pattern.compile("^[★●◆▪]");
        java.util.regex.Matcher symbolMatcher = symbolPattern.matcher(fullText);
        int symbolCount = 0;
        while (symbolMatcher.find() && symbolCount < 10) {
            System.out.println("  找到: " + symbolMatcher.group() + " 位置: " + symbolMatcher.start());
            symbolCount++;
        }
        if (symbolCount == 0) System.out.println("  未找到特殊符号模式");

        // 5. 统计换行模式
        System.out.println("\n5. 换行模式统计:");
        String[] lines = fullText.split("\n");
        int singleNewline = 0, doubleNewline = 0, tripleNewline = 0;
        for (int i = 0; i < lines.length - 1; i++) {
            if (lines[i].trim().isEmpty() && i+1 < lines.length && lines[i+1].trim().isEmpty()) {
                if (i+2 < lines.length && lines[i+2].trim().isEmpty()) {
                    tripleNewline++;
                    i += 2;
                } else {
                    doubleNewline++;
                    i += 1;
                }
            } else if (!lines[i].trim().isEmpty()) {
                singleNewline++;
            }
        }
        System.out.println("  单行换行: " + singleNewline);
        System.out.println("  双行换行: " + doubleNewline);
        System.out.println("  三行以上换行: " + tripleNewline);
    }

    private void analyzeTextFeatures(String text) {
        // 统计各类字符
        int chineseCount = 0, englishCount = 0, digitCount = 0, spaceCount = 0, newlineCount = 0;
        int punctuationCount = 0, codeLikeCount = 0;

        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FA5) chineseCount++;
            else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) englishCount++;
            else if (c >= '0' && c <= '9') digitCount++;
            else if (c == ' ') spaceCount++;
            else if (c == '\n') newlineCount++;
            else if (".,!?;:()[]{}+-*/=<>%&|^~".indexOf(c) >= 0) punctuationCount++;
            else if ("{}();=<>".indexOf(c) >= 0) codeLikeCount++; // 代码特征字符
        }

        System.out.println("中文字符: " + chineseCount);
        System.out.println("英文字符: " + englishCount);
        System.out.println("数字字符: " + digitCount);
        System.out.println("空格字符: " + spaceCount);
        System.out.println("换行字符: " + newlineCount);
        System.out.println("标点符号: " + punctuationCount);
        System.out.println("代码特征字符: " + codeLikeCount);

        // 检测是否有代码块特征
        boolean hasCodeBlocks = text.contains("public class") || text.contains("void main")
                || text.contains("System.out.println") || text.contains("@Test")
                || text.contains("import java.");
        System.out.println("疑似包含代码块: " + hasCodeBlocks);

        // 检测是否有HTML标签
        boolean hasHtmlTags = text.contains("<") && text.contains(">");
        System.out.println("疑似包含HTML标签: " + hasHtmlTags);
    }
}