package com.zhubao.zhuaiagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("file_metadata")
public class FileMetadata {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String originalFilename;
    private String storedPath;
    private String md5;
    private Integer version = 1;
    private Integer chunkCount = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}