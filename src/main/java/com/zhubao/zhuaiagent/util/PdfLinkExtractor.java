package com.zhubao.zhuaiagent.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class PdfLinkExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfLinkExtractor.class);

    /**
     * 从 PDF 中提取所有超链接锚点（Link Annotation）及其对应的文本
     * @param document PDDocument
     * @return 锚点列表，每个锚点包含页码、矩形区域、URI、文本
     */
    public List<LinkAnchor> extractLinkAnchors(PDDocument document) throws IOException {
        List<LinkAnchor> anchors = new ArrayList<>();
        for (int pageIdx = 0; pageIdx < document.getNumberOfPages(); pageIdx++) {
            PDPage page = document.getPage(pageIdx);
            for (PDAnnotation ann : page.getAnnotations()) {
                if (ann instanceof PDAnnotationLink link) {
                    PDRectangle rect = link.getRectangle();
                    if (rect == null) continue;

                    // 获取 URI（如果是外部链接）
                    String uri = null;
                    if (link.getAction() instanceof PDActionURI actionURI) {
                        uri = actionURI.getURI();
                    }

                    // 反查矩形区域内的文本
                    String text = extractTextAtRect(document, pageIdx, rect);

                    anchors.add(new LinkAnchor(pageIdx + 1, rect, uri, text));
                }
            }
        }
        return anchors;
    }

    /**
     * 提取指定矩形区域内的文本
     */
    private String extractTextAtRect(PDDocument document, int pageIdx, PDRectangle rect) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);
        // 将 PDRectangle 转换为 awt Rectangle2D（注意坐标转换：PDF 原点在左下，awt 在左上）
        // 这里使用简单的转换：y 坐标翻转（页面高度 - rect.getUpperRightY() 作为 top）
        PDPage page = document.getPage(pageIdx);
        float pageHeight = page.getMediaBox().getHeight();
        float x = rect.getLowerLeftX();
        float y = pageHeight - rect.getUpperRightY();
        float w = rect.getWidth();
        float h = rect.getHeight();
        stripper.addRegion("link", new Rectangle2D.Float(x, y, w, h));
        stripper.extractRegions(page);
        String text = stripper.getTextForRegion("link");
        return text != null ? text.trim() : "";
    }

    /**
     * 根据锚点列表切分 PDF 全文，返回题目列表
     * @param fullText PDFBox 提取的全文（PDFTextStripper.getText()）
     * @param anchors 锚点列表（已过滤出题目）
     * @return 题目列表
     */
    public List<String> splitByLinkAnchors(String fullText, List<LinkAnchor> anchors) {
        // 1. 筛选出真正的题目锚点（文本包含问号，且不是推广链接）
        List<LinkAnchor> questionAnchors = anchors.stream()
                .filter(a -> isQuestionTitle(a.text()))
                .sorted(Comparator.comparingInt(a -> fullText.indexOf(a.text())))
                .toList();

        if (questionAnchors.isEmpty()) {
            log.warn("未找到任何题目锚点，返回空列表");
            return Collections.emptyList();
        }

        // 2. 按锚点文本在全文中的位置切分
        List<String> questions = new ArrayList<>();
        int prevPos = 0;
        for (LinkAnchor anchor : questionAnchors) {
            int pos = fullText.indexOf(anchor.text(), prevPos);
            if (pos < 0) continue; // 理论上不应发生
            if (prevPos > 0) {
                // 取上一个锚点到当前锚点之间的内容
                String chunk = fullText.substring(prevPos, pos).trim();
                if (chunk.length() > 50) { // 过滤过短的碎片
                    questions.add(chunk);
                }
            }
            prevPos = pos;
        }
        // 最后一段
        if (prevPos < fullText.length()) {
            String lastChunk = fullText.substring(prevPos).trim();
            if (lastChunk.length() > 50) {
                questions.add(lastChunk);
            }
        }

        return questions;
    }

    /**
     * 判断一段文本是否为题目标题（包含问号，或以疑问词开头）
     */
    public boolean isQuestionTitle(String text) {
        if (text == null || text.isBlank()) return false;
        // 过滤推广链接
        if (text.contains("推荐") || text.contains("简历") || text.contains("面试鸭")
                || text.contains("编程导航") || text.contains("AI资源") || text.contains("学编程") ) {
            return false;
        }
        // 过滤过短的文本（如页码、页脚）
        if (text.length() < 5) return false;
        // 过滤不含中文字符的文本（如纯英文 URL 或数字）
        long chineseCount = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FA5).count();
        if (chineseCount == 0) return false;
        return true;
    }


    /**
     * 锚点记录
     */
    public record LinkAnchor(int page, PDRectangle rect, String uri, String text) {}
}