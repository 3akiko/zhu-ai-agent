package com.zhubao.zhuaiagent.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhubao.zhuaiagent.entity.FileMetadata;
import com.zhubao.zhuaiagent.mapper.FileMetadataMapper;
import org.springframework.stereotype.Service;

@Service
public class FileMetadataService extends ServiceImpl<FileMetadataMapper, FileMetadata> {

    public FileMetadata findByUserIdAndMd5(String userId, String md5) {
        return lambdaQuery()
                .eq(FileMetadata::getUserId, userId)
                .eq(FileMetadata::getMd5, md5)
                .one();
    }

    public FileMetadata findByUserIdAndOriginalFilename(String userId, String filename) {
        return lambdaQuery()
                .eq(FileMetadata::getUserId, userId)
                .eq(FileMetadata::getOriginalFilename, filename)
                .one();
    }
}