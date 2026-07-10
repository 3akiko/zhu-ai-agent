package com.zhubao.zhuaiagent.service;

import com.zhubao.zhuaiagent.entity.FileMetadata;
import com.zhubao.zhuaiagent.mapper.FileMetadataMapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileMetadataServiceTest {

    @Resource
    private FileMetadataMapper fileMetadataMapper;

    @Resource
    private FileMetadataService fileMetadataService;

    @Test
    void findByUserIdAndMd5() {
        FileMetadata fileMetadata = new FileMetadata().setId(1L)
                .setUserId("hdztest")
                .setOriginalFilename("test111.filename")
                .setMd5("md5")
                .setChunkCount(10).setStoredPath("./upload/test111.filename");
        fileMetadataMapper.updateById(fileMetadata);
    }

    @Test
    void findByUserIdAndOriginalFilename() {
        System.out.println(fileMetadataService.findByUserIdAndMd5("hdztest", "md5"));
    }


    public static void main(String[] args) {
        System.out.println(Paths.get("./uploads").toAbsolutePath());
    }
}