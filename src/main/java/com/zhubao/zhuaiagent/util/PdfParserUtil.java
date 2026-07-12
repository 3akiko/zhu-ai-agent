package com.zhubao.zhuaiagent.util;

import com.google.common.collect.Lists;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import jakarta.annotation.Resource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@Slf4j
public class PdfParserUtil {

    private static final EncodingRegistry REGISTRY = Encodings.newLazyEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    @Resource
    private PdfLinkExtractor pdfLinkExtractor;


    public List<QuestionChunk> splitByQuestions(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        String fullText = stripper.getText(document);

        List<PdfLinkExtractor.LinkAnchor> anchors = pdfLinkExtractor.extractLinkAnchors(document);

        // 筛选题目锚点：必须是题目标题且有有效 URI
        List<PdfLinkExtractor.LinkAnchor> questionAnchors = anchors.stream()
                .filter(a -> pdfLinkExtractor.isQuestionTitle(a.text()))
                .filter(a -> isValidSourceUrl(a.uri()))
                .sorted(Comparator.comparingInt(a -> fullText.indexOf(a.text())))
                .toList();

        if (questionAnchors.size() < 5) {
            log.warn("锚点很少，进行兜底拆分");
            return Lists.newArrayList(new QuestionChunk(fullText, null));
        }

        List<QuestionChunk> chunks = new ArrayList<>();

        // 从第二个锚点开始，取上一个锚点到当前锚点之间的内容
        for (int i = 1; i < questionAnchors.size(); i++) {
            PdfLinkExtractor.LinkAnchor prevAnchor = questionAnchors.get(i - 1);
            PdfLinkExtractor.LinkAnchor currAnchor = questionAnchors.get(i);

            int prevPos = fullText.indexOf(prevAnchor.text());
            int currPos = fullText.indexOf(currAnchor.text(), prevPos);
            if (prevPos < 0 || currPos < 0) continue;

            String segment = fullText.substring(prevPos, currPos).trim();
            // 过滤过短的碎片（如推广信息）
            if (segment.length() > 50) {  // 阈值可根据实际情况调整
                chunks.add(new QuestionChunk(segment, prevAnchor.uri()));
            }
        }

        // 最后一个锚点之后的内容（包含最后一个锚点本身）
        PdfLinkExtractor.LinkAnchor lastAnchor = questionAnchors.get(questionAnchors.size() - 1);
        int lastPos = fullText.indexOf(lastAnchor.text());
        if (lastPos >= 0) {
            String lastSegment = fullText.substring(lastPos).trim();
            if (lastSegment.length() > 50) {
                chunks.add(new QuestionChunk(lastSegment, lastAnchor.uri()));
            }
        }

        return chunks;
    }

    /**
     * 判断 URI 是否有效（不为空且不是推广链接）
     */
    private boolean isValidSourceUrl(String uri) {
        if (uri == null || uri.isBlank()) return false;
        // 过滤推广链接，如 https://www.mianshiya.com/
        if (uri.equals("https://www.mianshiya.com/") || uri.equals("https://www.mianshiya.com")) {
            return false;
        }
        return true;
    }

    /**
     * 兜底方案，将大文本当成一个题目进行才分
     * @param fullText
     * @return
     */
    private List<QuestionChunk> fallbackSplit(String fullText) {
        // 将整个文本视为一个大题目，用 splitQuestionIntoChunks 分块
        List<String> chunks = splitQuestionIntoChunks(fullText, 1024, 128);
        return chunks.stream()
                .filter(chunk -> chunk.length() > 50)
                .map(chunk -> new QuestionChunk(chunk, null))
                .toList();
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

    @Getter
    @Setter
    public static class QuestionChunk {
        private String text;
        private String sourceUrl;

        public QuestionChunk(String text, String sourceUrl) {
            this.text = text;
            this.sourceUrl = sourceUrl;
        }
    }


}