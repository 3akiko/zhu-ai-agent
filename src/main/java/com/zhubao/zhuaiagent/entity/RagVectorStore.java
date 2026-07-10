package com.zhubao.zhuaiagent.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class RagVectorStore {
    private Long id;
    private Long fileMetadataId;
    private String content;
    private String embedding; // 字符串形式
    private String metadata;
    private LocalDateTime createdAt;
}