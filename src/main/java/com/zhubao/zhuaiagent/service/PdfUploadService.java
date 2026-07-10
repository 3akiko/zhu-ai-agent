package com.zhubao.zhuaiagent.service;

import com.zhubao.zhuaiagent.config.PathConfig;
import com.zhubao.zhuaiagent.entity.FileMetadata;
import com.zhubao.zhuaiagent.util.PdfParserUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class PdfUploadService {

    @Autowired
    private FileMetadataService fileMetadataService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EmbeddingModel dashscopeEmbeddingModel;

    @Autowired
    private PdfParserUtil pdfParserUtil;

    @Autowired
    private PathConfig pathConfig;

    @Transactional(rollbackFor = Exception.class)
    public Long uploadPdf(MultipartFile file, String userId) throws IOException {
        // 1. 计算 MD5
        String md5 = DigestUtils.md5DigestAsHex(file.getInputStream());

        // 2. 查找是否已存在相同文件名（同一用户）
        FileMetadata existingByFilename = fileMetadataService.findByUserIdAndOriginalFilename(userId, file.getOriginalFilename());

        if (existingByFilename != null) {
            // 同名文件已存在
            if (existingByFilename.getMd5().equals(md5)) {
                // 内容未变，直接返回旧 ID
                log.info("文件内容未变化，直接返回已有记录，fileName = {}", file.getOriginalFilename());
                return existingByFilename.getId();
            } else {
                // 内容已更新，先删除旧数据
                log.info("文件内容已更新，删除旧数据，fileName = {}", file.getOriginalFilename());
                deleteOldFileData(existingByFilename.getId(), existingByFilename.getStoredPath());
            }
        }

        // 3. 保存文件到磁盘
        String storedPath = saveFileToDisk(file, userId);

        // 4. 创建 file_metadata 记录
        FileMetadata meta = new FileMetadata();
        meta.setUserId(userId);
        meta.setOriginalFilename(file.getOriginalFilename());
        meta.setStoredPath(storedPath);
        meta.setMd5(md5);
        meta.setVersion(1);
        meta.setCreatedAt(LocalDateTime.now());
        meta.setUpdatedAt(LocalDateTime.now());
        fileMetadataService.save(meta);
        Long fileMetadataId = meta.getId();

        // 5. 使用 PDFBox 提取文本
        List<String> questions;
        try (PDDocument document = Loader.loadPDF(new File(storedPath))) {
            questions = pdfParserUtil.splitByQuestions(document);
        }


        // 7. 对每道题按 token 精确分块（maxTokens=1024, overlapTokens=128）
        List<String> allChunks = new ArrayList<>();
        for (String question : questions) {
            List<String> subChunks = pdfParserUtil.splitQuestionIntoChunks(question, 1024, 128);
            allChunks.addAll(subChunks);
        }

        // 8. 批量向量化
        List<float[]> embeddings = batchEmbed(allChunks, 10);

        // 9. 批量插入 rag_vector_store
        batchInsertChunks(fileMetadataId, allChunks, embeddings);

        // 10. 更新 file_metadata 的 chunk_count
        fileMetadataService.lambdaUpdate()
                .eq(FileMetadata::getId, fileMetadataId)
                .set(FileMetadata::getChunkCount, allChunks.size())
                .set(FileMetadata::getUpdatedAt, LocalDateTime.now())
                .update();

        return fileMetadataId;
    }

    private String saveFileToDisk(MultipartFile file, String userId) throws IOException {
        Path dir = Path.of(pathConfig.getUploadDir(), userId, UUID.randomUUID().toString());
        Files.createDirectories(dir);
        Path targetPath = dir.resolve(file.getOriginalFilename());
        file.transferTo(targetPath.toFile());
        return targetPath.toString();
    }


    /**
     * 删除旧文件关联的所有数据
     * @param oldFileMetadataId 旧文件元数据 ID
     */
    private void deleteOldFileData(Long oldFileMetadataId, String storedPath) {
        // 1. 删除 rag_vector_store 中关联的向量数据
        String deleteSql = "DELETE FROM rag_vector_store WHERE file_metadata_id = ?";
        jdbcTemplate.update(deleteSql, oldFileMetadataId);

        //2. 删除 file_metadata 记录（可选）
        fileMetadataService.removeById(oldFileMetadataId);

         //3. 删除物理文件（可选，根据需求决定是否保留）,注意别误删本地文件了
         if (StringUtils.isNotBlank(storedPath)) {
             try {
                 Files.deleteIfExists(Path.of(storedPath));
             } catch (IOException e) {
                 log.warn("删除旧文件失败: {}", storedPath, e);
             }
         }

        log.info("已删除旧文件关联数据，fileMetadataId = {}", oldFileMetadataId);
    }

    private void batchInsertChunks(Long fileMetadataId, List<String> contents, List<float[]> embeddings) {
        String sql = "INSERT INTO rag_vector_store (file_metadata_id, content, embedding, metadata) VALUES (?, ?, ?::vector, ?::jsonb)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, fileMetadataId);
                ps.setString(2, contents.get(i));
                ps.setString(3, floatArrayToPgVector(embeddings.get(i)));
                Map<String, Object> meta = new HashMap<>();
                meta.put("chunkIndex", i);
                meta.put("source", "pdf-upload");
                ps.setString(4, toJson(meta));
            }

            @Override
            public int getBatchSize() {
                return contents.size();
            }
        });
    }

    private String floatArrayToPgVector(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<float[]> batchEmbed(List<String> texts, int batchSize) {
        List<float[]> allEmbeddings = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            allEmbeddings.addAll(dashscopeEmbeddingModel.embed(batch));
        }
        return allEmbeddings;
    }


    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization error", e);
        }
    }
}