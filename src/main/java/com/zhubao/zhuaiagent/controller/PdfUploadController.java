package com.zhubao.zhuaiagent.controller;

import com.zhubao.zhuaiagent.common.ApiResponse;
import com.zhubao.zhuaiagent.service.PdfUploadService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/upload")
@Slf4j
public class PdfUploadController {

    @Autowired
    private PdfUploadService pdfUploadService;

    @PostMapping("/add")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") String userId) {
        //校验
        Validate.notBlank(userId, "uid不能为空");
        Validate.notNull(file, "未上传文件");
        Validate.isTrue(StringUtils.isNotBlank(file.getOriginalFilename()), "文件名不能为空");
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "文件为空"));
        }

        try {
            Long documentId = pdfUploadService.uploadPdf(file, userId);
            return ResponseEntity.ok(
                    ApiResponse.success("上传成功", Map.of("documentId", documentId))
            );
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("上传失败: " + e.getMessage()));
        }
    }
}