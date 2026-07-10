package com.zhubao.zhuaiagent.config;

import com.zhubao.zhuaiagent.constant.FileConstant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class PathConfig {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;


    public String getUploadDir() {
        return FileConstant.FILE_SAVE_DIR + uploadDir;
    }
}
