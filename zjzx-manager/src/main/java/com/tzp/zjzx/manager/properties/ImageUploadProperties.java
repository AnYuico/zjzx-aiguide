package com.tzp.zjzx.manager.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@Data
@ConfigurationProperties(prefix = "zjzx.upload.image")
public class ImageUploadProperties {

    private DataSize maxSize = DataSize.ofMegabytes(5);
    private int maxWidth = 8192;
    private int maxHeight = 8192;
    private long maxPixels = 25_000_000L;
}
