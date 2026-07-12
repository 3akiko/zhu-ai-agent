package com.zhubao.zhuaiagent.entity;

public record Reference(
    int index,
    String filename,
    Integer page,
    Integer chunkIndex,
    String snippet,
    String sourceUrl
) {}