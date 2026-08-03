package com.tzp.zjzx.manager.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "zjzx.minio")
public class MinioProperties {

    private  String endPointUrl;
    private  String accessKey;
    private  String secreKey;
    private  String bucketName;

}
