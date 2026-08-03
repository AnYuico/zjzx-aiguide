package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.properties.MinioProperties;
import com.tzp.zjzx.manager.upload.ImageFileType;
import com.tzp.zjzx.manager.upload.ImageUploadValidator;
import com.tzp.zjzx.manager.upload.ValidatedImage;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileUploadServiceImplTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private ImageUploadValidator imageUploadValidator;

    private FileUploadServiceImpl fileUploadService;

    @BeforeEach
    void setUp() {
        MinioProperties properties = new MinioProperties();
        properties.setEndPointUrl("http://minio.example.test:9000/");
        properties.setBucketName("zjzx-bucket");
        fileUploadService = new FileUploadServiceImpl(
                minioClient, properties, imageUploadValidator);
    }

    @Test
    void storesValidatedImageWithGeneratedNameAndDetectedContentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dangerous.svg", "image/svg+xml", new byte[]{1, 2, 3});
        when(imageUploadValidator.validate(file))
                .thenReturn(new ValidatedImage(ImageFileType.JPEG, 10, 10));

        String url = fileUploadService.upload(file);

        ArgumentCaptor<PutObjectArgs> argsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(argsCaptor.capture());
        PutObjectArgs args = argsCaptor.getValue();
        assertEquals("zjzx-bucket", args.bucket());
        assertEquals("image/jpeg", args.contentType());
        assertTrue(args.object().matches("\\d{8}/[0-9a-f]{32}\\.jpg"));
        assertFalse(args.object().contains("dangerous"));
        assertEquals("http://minio.example.test:9000/zjzx-bucket/" + args.object(), url);
    }

    @Test
    void mapsStorageFailureToDedicatedBusinessError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[]{1, 2, 3});
        when(imageUploadValidator.validate(file))
                .thenReturn(new ValidatedImage(ImageFileType.PNG, 10, 10));
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new IllegalStateException("storage unavailable"));

        MyException exception = assertThrows(MyException.class, () -> fileUploadService.upload(file));

        assertEquals(ResultCodeEnum.UPLOAD_STORAGE_ERROR, exception.getResultCodeEnum());
    }
}
