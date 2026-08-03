package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.properties.MinioProperties;
import com.tzp.zjzx.manager.service.FileUploadService;
import com.tzp.zjzx.manager.upload.ImageUploadValidator;
import com.tzp.zjzx.manager.upload.ValidatedImage;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class FileUploadServiceImpl implements FileUploadService {

    private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;
    private final ImageUploadValidator imageUploadValidator;

    public FileUploadServiceImpl(MinioClient minioClient,
                                 MinioProperties minioProperties,
                                 ImageUploadValidator imageUploadValidator) {
        this.minioClient = minioClient;
        this.minioProperties = minioProperties;
        this.imageUploadValidator = imageUploadValidator;
    }

    @Override
    public String upload(MultipartFile file) {
        ValidatedImage image = imageUploadValidator.validate(file);
        String objectName = buildObjectName(image);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .contentType(image.getType().getContentType())
                            .stream(inputStream, file.getSize(), -1)
                            .build()
            );
            return buildPublicUrl(objectName);
        } catch (Exception ex) {
            log.error("Image upload failed: bucket={}, object={}",
                    minioProperties.getBucketName(), objectName, ex);
            throw new MyException(ResultCodeEnum.UPLOAD_STORAGE_ERROR);
        }
    }

    private String buildObjectName(ValidatedImage image) {
        String dateDirectory = LocalDate.now().format(DATE_DIRECTORY_FORMATTER);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return dateDirectory + "/" + uuid + "." + image.getType().getExtension();
    }

    private String buildPublicUrl(String objectName) {
        String endpoint = minioProperties.getEndPointUrl();
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "/" + minioProperties.getBucketName() + "/" + objectName;
    }
}
