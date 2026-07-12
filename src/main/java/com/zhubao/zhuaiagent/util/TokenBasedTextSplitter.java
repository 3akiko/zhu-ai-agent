package com.zhubao.zhuaiagent.util;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import com.knuddels.jtokkit.api.IntArrayList;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于 token 计数的文本分块工具
 * 优先按段落（双换行）分割，段落过长时按句子分割，再按 token 数合并，带重叠
 */
@Slf4j
public class TokenBasedTextSplitter {

    private static final EncodingRegistry REGISTRY = Encodings.newLazyEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private final int maxTokens;
    private final int overlapTokens;

    public TokenBasedTextSplitter(int maxTokens, int overlapTokens) {
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    /**
     * 将文本分块
     * @param text 待分块的文本
     * @return 分块列表
     */
    public List<String> splitText(String text) {
        // 1. 按段落（连续两个及以上换行）分割
        String[] paragraphs = text.split("\\n{2,}");
        List<String> initialSegments = new ArrayList<>();
        for (String para : paragraphs) {
            para = para.trim();
            if (!para.isEmpty()) {
                initialSegments.add(para);
            }
        }

        // 2. 对每个段落递归分块
        List<String> chunks = new ArrayList<>();
        for (String segment : initialSegments) {
            List<String> subChunks = splitSegment(segment);
            chunks.addAll(subChunks);
        }
        return chunks;
    }

    /**
     * 递归分块一个段落
     */
    private List<String> splitSegment(String text) {
        List<String> result = new ArrayList<>();
        if (text.isEmpty()) return result;

        // 计算当前文本的 token 数
        int tokenCount = ENCODING.encode(text).size();
        if (tokenCount <= maxTokens) {
            result.add(text);
            return result;
        }

        // 尝试按句子分割（句号、问号、感叹号、换行）
        String[] sentences = text.split("(?<=[。？！.!?\\n])");
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;

            int currentTokens = ENCODING.encode(current.toString()).size();
            int sentenceTokens = ENCODING.encode(trimmed).size();

            if (currentTokens + sentenceTokens > maxTokens) {
                if (current.length() > 0) {
                    merged.add(current.toString().trim());
                }
                current = new StringBuilder(trimmed);
            } else {
                current.append(trimmed);
            }
        }
        if (current.length() > 0) {
            merged.add(current.toString().trim());
        }

        // 处理合并后的块：如果仍有超长块，按 token 硬切
        for (String seg : merged) {
            if (ENCODING.encode(seg).size() <= maxTokens) {
                result.add(seg);
            } else {
                // 硬切，并保留重叠
                result.addAll(hardSplit(seg));
            }
        }

        return result;
    }

    /**
     * 按 token 数硬切，带重叠
     */
    private List<String> hardSplit(String text) {
        List<String> chunks = new ArrayList<>();
        IntArrayList allTokens = ENCODING.encode(text);
        int totalTokens = allTokens.size();
        int start = 0;

        while (start < totalTokens) {
            int end = Math.min(start + maxTokens, totalTokens);
            // 提取 [start, end) 的 token 子序列
            IntArrayList chunkTokens = new IntArrayList();
            for (int i = start; i < end; i++) {
                chunkTokens.add(allTokens.get(i));
            }
            String chunk = ENCODING.decode(chunkTokens).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            // 移动 start，保留重叠
            start = end - overlapTokens;
            if (start < 0) start = 0;
            // 防止死循环
            if (end >= totalTokens) break;
        }
        return chunks;
    }

    /**
     * 提取文本最后 N 个 token 对应的子串（用于重叠）
     */
    public static String extractLastTokens(String text, int tokenCount) {
        if (tokenCount <= 0) return "";
        IntArrayList tokens = ENCODING.encode(text);
        int total = tokens.size();
        if (total <= tokenCount) return text;
        int start = total - tokenCount;
        IntArrayList lastTokens = new IntArrayList();
        for (int i = start; i < total; i++) {
            lastTokens.add(tokens.get(i));
        }
        return ENCODING.decode(lastTokens);
    }
}