package com.zhubao.zhuaiagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.observation.conventions.VectorStoreSimilarityMetric;
import org.springframework.ai.vectorstore.AbstractVectorStoreBuilder;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;

import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ObservedHybridVectorStore extends AbstractObservationVectorStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tableName;
    private final String schemaName;
    private final int dimensions;
    private final PgDistanceType distanceType;

    private static final Map<PgDistanceType, VectorStoreSimilarityMetric> SIMILARITY_TYPE_MAPPING = Map.of(
            PgDistanceType.COSINE_DISTANCE, VectorStoreSimilarityMetric.COSINE,
            PgDistanceType.EUCLIDEAN_DISTANCE, VectorStoreSimilarityMetric.EUCLIDEAN,
            PgDistanceType.NEGATIVE_INNER_PRODUCT, VectorStoreSimilarityMetric.DOT
    );

    // 枚举类型，与 PgVectorStore 保持一致
    public enum PgDistanceType {
        COSINE_DISTANCE,
        EUCLIDEAN_DISTANCE,
        NEGATIVE_INNER_PRODUCT
    }

    // 私有构造器，只接受 ObservedHybridVectorStoreBuilder
    protected ObservedHybridVectorStore(ObservedHybridVectorStoreBuilder observedHybridVectorStoreBuilder) {
        super(observedHybridVectorStoreBuilder);
        this.jdbcTemplate = observedHybridVectorStoreBuilder.jdbcTemplate;
        this.objectMapper = observedHybridVectorStoreBuilder.objectMapper;
        this.tableName = observedHybridVectorStoreBuilder.tableName;
        this.schemaName = observedHybridVectorStoreBuilder.schemaName;
        this.dimensions = observedHybridVectorStoreBuilder.dimensions;
        this.distanceType = observedHybridVectorStoreBuilder.distanceType;
    }

    @Override
    public void doAdd(List<Document> documents) {
        log.warn("ObservedHybridVectorStore.doAdd() 未实现，入库请使用 PdfUploadService");
        throw new UnsupportedOperationException("入库请使用 PdfUploadService");
    }

    @Override
    public void doDelete(List<String> idList) {
        log.warn("ObservedHybridVectorStore.doDelete() 未实现，删除请使用 PdfUploadService");
        throw new UnsupportedOperationException("删除请使用 PdfUploadService");
    }

    @Override
    public List<Document> doSimilaritySearch(SearchRequest request) {
        String query = request.getQuery();
        int topK = request.getTopK();
        double similarityThreshold = request.getSimilarityThreshold();

        // 1. 向量化查询文本
        float[] queryVector = embeddingModel.embed(query);

        // 2. 从 filterExpression 中提取 fileMetadataId
        Long fileMetadataId = extractFileMetadataId(request.getFilterExpression());

        // 3. 构建混合检索 SQL（使用 schemaName.tableName）
        String fullTableName = schemaName != null && !schemaName.isBlank()
                ? schemaName + "." + tableName
                : tableName;
        String sql = buildHybridSearchSql(fullTableName);
        log.debug("混合检索 SQL: {}", sql);

        List<Document> results = jdbcTemplate.query(
                sql,
                this::mapRowToDocument,
                // vec 路参数
                new PGvector(queryVector),
                fileMetadataId,
                fileMetadataId,
                new PGvector(queryVector),
                // lex 路参数
                query,
                query,
                fileMetadataId,
                fileMetadataId,
                // 最终 LIMIT
                topK
        );

        // 4. 按相似度阈值过滤
        if (similarityThreshold > 0) {
            results = results.stream()
                    .filter(doc -> (double) doc.getMetadata().getOrDefault("rrf_score", 0.0) >= similarityThreshold)
                    .collect(Collectors.toList());
        }

        return results;
    }

    @Override
    public VectorStoreObservationContext.Builder createObservationContextBuilder(String operationName) {
        return VectorStoreObservationContext.builder(VectorStoreProvider.PG_VECTOR.value(), operationName)
                .collectionName(tableName)
                .dimensions(dimensions)
                .namespace(schemaName)
                .similarityMetric(getSimilarityMetric());
    }


    private String getSimilarityMetric() {
        return !SIMILARITY_TYPE_MAPPING.containsKey(distanceType) ? this.distanceType.name() : ((VectorStoreSimilarityMetric)SIMILARITY_TYPE_MAPPING.get(this.distanceType)).value();
    }

    private String buildHybridSearchSql(String fullTableName) {
        return String.format("""
            WITH vec AS (
                SELECT id, 1 - (embedding <=> ?::vector) AS score, 'vec' AS source
                FROM %s
                WHERE (?::bigint IS NULL OR file_metadata_id = ?)
                ORDER BY embedding <=> ?::vector
                LIMIT 30
            ),
            lex AS (
                SELECT id, ts_rank(content_tsv, websearch_to_tsquery('simple', ?)) AS score, 'lex' AS source
                FROM %s
                WHERE content_tsv @@ websearch_to_tsquery('simple', ?)
                  AND (?::bigint IS NULL OR file_metadata_id = ?)
                ORDER BY score DESC
                LIMIT 30
            ),
            ranked AS (
                SELECT id, score, source, ROW_NUMBER() OVER (PARTITION BY source ORDER BY score DESC) AS rank
                FROM (
                    SELECT id, score, source FROM vec
                    UNION ALL
                    SELECT id, score, source FROM lex
                ) combined
            ),
            fused AS (
                SELECT id,
                       MAX(CASE WHEN source = 'vec' THEN score END) AS vec_score,
                       MAX(CASE WHEN source = 'lex' THEN score END) AS lex_score,
                       SUM(1.0 / (60 + rank)) AS rrf_score
                FROM ranked
                GROUP BY id
            )
            SELECT r.id, r.content, r.metadata, f.rrf_score, f.vec_score, f.lex_score
            FROM fused f
            JOIN %s r ON r.id = f.id
            ORDER BY f.rrf_score DESC
            LIMIT ?
            """, fullTableName, fullTableName, fullTableName);
    }

    private Document mapRowToDocument(ResultSet rs, int rowNum) throws SQLException {
        String id = rs.getString("id");
        String content = rs.getString("content");
        String metadataJson = rs.getString("metadata");
        double rrfScore = rs.getDouble("rrf_score");
        double vecScore = rs.getDouble("vec_score");
        double lexScore = rs.getDouble("lex_score");

        Map<String, Object> metadata = new HashMap<>();
        if (metadataJson != null) {
            try {
                metadata = objectMapper.readValue(metadataJson, Map.class);
            } catch (Exception e) {
                log.warn("解析 metadata JSON 失败: {}", metadataJson, e);
            }
        }
        metadata.put("rrf_score", rrfScore);
        metadata.put("vec_score", vecScore);
        metadata.put("lex_score", lexScore);

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .score(rrfScore)  // 设置 Document 的 score 属性
                .build();
    }

    private Long extractFileMetadataId(Object filterExpression) {
        if (filterExpression == null) return null;
        String exprStr = filterExpression.toString();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "fileMetadataId\\s*(==|=)\\s*(\\d+)"
        );
        java.util.regex.Matcher matcher = pattern.matcher(exprStr);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(2));
        }
        return null;
    }

    // ========== ObservedHybridVectorStoreBuilder ==========
    public static class ObservedHybridVectorStoreBuilder extends AbstractVectorStoreBuilder<ObservedHybridVectorStoreBuilder> {
        private JdbcTemplate jdbcTemplate;
        private ObjectMapper objectMapper;
        private String tableName = "rag_vector_store";
        private String schemaName = "public";
        private int dimensions = 1536;
        private PgDistanceType distanceType = PgDistanceType.COSINE_DISTANCE;

        public ObservedHybridVectorStoreBuilder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
            super(embeddingModel);
            this.jdbcTemplate = jdbcTemplate;
        }

        public ObservedHybridVectorStoreBuilder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public ObservedHybridVectorStoreBuilder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public ObservedHybridVectorStoreBuilder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public ObservedHybridVectorStoreBuilder dimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public ObservedHybridVectorStoreBuilder distanceType(PgDistanceType distanceType) {
            this.distanceType = distanceType;
            return this;
        }

        public ObservedHybridVectorStore build() {
            // 校验必要参数
            if (this.jdbcTemplate == null) {
                throw new IllegalArgumentException("jdbcTemplate must not be null");
            }
            if (this.objectMapper == null) {
                this.objectMapper = new ObjectMapper(); // 默认创建
            }
            return new ObservedHybridVectorStore(this);
        }
    }

    // 静态工厂方法
    public static ObservedHybridVectorStoreBuilder builder(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return new ObservedHybridVectorStoreBuilder(jdbcTemplate, embeddingModel);
    }
}