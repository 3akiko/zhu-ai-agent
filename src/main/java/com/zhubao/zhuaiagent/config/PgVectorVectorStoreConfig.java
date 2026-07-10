package com.zhubao.zhuaiagent.config;

import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgDistanceType.COSINE_DISTANCE;
import static org.springframework.ai.vectorstore.pgvector.PgVectorStore.PgIndexType.HNSW;

// 为方便开发调试和部署，临时注释，如果需要使用 PgVector 存储知识库，取消注释即可
@Configuration
public class PgVectorVectorStoreConfig {

    /**
     * BatchingStrategy：1.1 里 PgVectorStore builder 接收的是 BatchingStrategy 接口
     * （不再是 1.0 早期那种 Consumer<List<Document>> 风格）
     * 这里用 TokenCountBatchingStrategy，DeepSeek embedding 兼容 OpenAI 的 cl100k 编码
     */
    @Bean
    @ConditionalOnMissingBean(BatchingStrategy.class)
    public BatchingStrategy tokenCountBatchingStrategy() {
        return new TokenCountBatchingStrategy(
                EncodingType.CL100K_BASE,  // DeepSeek / OpenAI 都用这个
                8000,                       // maxInputTokenCount（留 10% reserve 后实际 ~7200）
                0.1                         // reservePercentage
        );
    }

    @Bean
    public VectorStore pgVectorVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel dashscopeEmbeddingModel,
                                           BatchingStrategy batchingStrategy) {
        VectorStore vectorStore = PgVectorStore.builder(jdbcTemplate, dashscopeEmbeddingModel)
                .schemaName("public")                  // 默认 public，显式写出
                .vectorTableName("rag_vector_store")     // 用你之前设计的表名，不是默认 vector_store
                .idType(PgVectorStore.PgIdType.BIGSERIAL)            // 你 document_chunk.id 是 BIGSERIAL
                .dimensions(1536)                      // embedding 维度
                .distanceType(COSINE_DISTANCE)         // 归一化向量用余弦
                .indexType(HNSW)                       // HNSW，默认 m=16
                .initializeSchema(false)               // ⚠️ 你表已手动建好（含 content_tsv + GIN），设 false
                .removeExistingVectorStoreTable(false) // 生产千万别 true
                .vectorTableValidationsEnabled(false)   // 表结构你自己管，不校验
                .maxDocumentBatchSize(1000)            // 你 /upload 接口分批用
                .batchingStrategy(batchingStrategy)    // 上面那个 TokenCountBatchingStrategy
                .build();
        return vectorStore;
    }
}
