package com.zhubao.zhuaiagent.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class PdfParserUtil {

    private static final EncodingRegistry REGISTRY = Encodings.newLazyEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    @Resource
    private PdfLinkExtractor pdfLinkExtractor;


    public List<String> splitByQuestions(PDDocument document) throws IOException {
        // 1. 提取全文文本
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(document);

        // 2. 提取超链接锚点
        List<PdfLinkExtractor.LinkAnchor> anchors = pdfLinkExtractor.extractLinkAnchors(document);

        // 3. 按锚点切分题目
        return pdfLinkExtractor.splitByLinkAnchors(fullText, anchors);
    }


    /**
     * 将单个题目（题干+讲解）按 token 数精确分块
     * @param questionText 单个题目的完整文本
     * @param maxTokens 每块最大 token 数
     * @param overlapTokens 重叠 token 数
     * @return 分块列表
     */
    public List<String> splitQuestionIntoChunks(String questionText, int maxTokens, int overlapTokens) {
        log.info("splitQuestionIntoChunks start");
        // 先按句子分割（使用简单的规则：句号、问号、感叹号、换行）
        List<String> sentences = splitSentences(questionText);

        List<String> chunks = new ArrayList<>();
        List<Integer> currentChunkTokens = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            List<Integer> sentenceTokens = ENCODING.encode(sentence).boxed();
            // 如果当前 chunk 加上这个句子会超 maxTokens，先保存当前 chunk
            if (currentChunkTokens.size() + sentenceTokens.size() > maxTokens) {
                if (!currentChunk.isEmpty()) {
                    chunks.add(currentChunk.toString());
                    // 重叠处理：保留最后 overlapTokens 个 token 对应的文本
                    String overlapText = extractLastTokens(currentChunk.toString(), overlapTokens);
                    currentChunk = new StringBuilder(overlapText);
                    currentChunkTokens = ENCODING.encode(overlapText).boxed();
                }
            }
            currentChunk.append(sentence);
            currentChunkTokens.addAll(sentenceTokens);
        }
        // 最后一块
        if (!currentChunk.isEmpty()) {
            chunks.add(currentChunk.toString());
        }
        log.info("splitQuestionIntoChunks end");
        return chunks;
    }

    /**
     * 简单的句子分割：按句号、问号、感叹号、换行分割，保留分隔符
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        // 匹配句号、问号、感叹号、换行（保留分隔符）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?<=[。？！.!?\\n])");
        String[] parts = pattern.split(text);
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                sentences.add(part.trim());
            }
        }
        return sentences;
    }

    /**
     * 提取文本最后 N 个 token 对应的子串（近似）
     */
    private String extractLastTokens(String text, int tokenCount) {
        if (tokenCount <= 0) {
            return "";
        }
        // 编码得到原始 IntList（原生整型列表，无装箱）
        IntArrayList tokenIntList = ENCODING.encode(text);
        int totalSize = tokenIntList.size();
        if (totalSize <= tokenCount) {
            return text;
        }

        // 截取末尾 N 个token，转成 jtokkit 要求的 IntArrayList
        int startIndex = totalSize - tokenCount;
        IntArrayList lastTokens = new IntArrayList();
        for (int i = startIndex; i < totalSize; i++) {
            lastTokens.add(tokenIntList.get(i));
        }

        // decode 接收 IntArrayList 原生类型
        return ENCODING.decode(lastTokens);
    }
}