package com.tzp.zjzx.manager.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    /**
     * 上传文件
     * @param file
     * @return
     */
    String upload(MultipartFile file);

}
