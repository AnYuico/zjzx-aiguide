package com.tzp.zjzx.manager.upload;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.properties.ImageUploadProperties;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;

@Component
public class ImageUploadValidator {

    private static final int HEADER_LENGTH = 32;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final ImageUploadProperties properties;

    public ImageUploadValidator(ImageUploadProperties properties) {
        this.properties = properties;
    }

    public ValidatedImage validate(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > properties.getMaxSize().toBytes()) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_TOO_LARGE);
        }

        byte[] header = readHeader(file);
        ImageFileType type = detectType(header);
        ImageDimensions dimensions = type == ImageFileType.WEBP
                ? readWebpDimensions(file)
                : readImageIoDimensions(file, type);
        validateDimensions(dimensions);
        return new ValidatedImage(type, dimensions.width(), dimensions.height());
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return inputStream.readNBytes(HEADER_LENGTH);
        } catch (IOException ex) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
    }

    private ImageFileType detectType(byte[] header) {
        if (header.length >= 3
                && unsigned(header[0]) == 0xff
                && unsigned(header[1]) == 0xd8
                && unsigned(header[2]) == 0xff) {
            return ImageFileType.JPEG;
        }
        if (startsWith(header, PNG_SIGNATURE)) {
            return ImageFileType.PNG;
        }
        if (header.length >= 16
                && asciiEquals(header, 0, "RIFF")
                && asciiEquals(header, 8, "WEBP")
                && (asciiEquals(header, 12, "VP8 ")
                || asciiEquals(header, 12, "VP8L")
                || asciiEquals(header, 12, "VP8X"))) {
            return ImageFileType.WEBP;
        }
        throw new MyException(ResultCodeEnum.UPLOAD_FILE_TYPE_NOT_ALLOWED);
    }

    private ImageDimensions readImageIoDimensions(MultipartFile file, ImageFileType expectedType) {
        try (InputStream inputStream = file.getInputStream();
             ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
            }
            ImageReader reader = readers.next();
            try {
                String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!matchesExpectedFormat(expectedType, formatName)) {
                    throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
                }
                reader.setInput(imageInputStream, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (MyException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
    }

    private boolean matchesExpectedFormat(ImageFileType expectedType, String formatName) {
        return expectedType == ImageFileType.JPEG
                ? "jpeg".equals(formatName) || "jpg".equals(formatName)
                : "png".equals(formatName);
    }

    private ImageDimensions readWebpDimensions(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] riffHeader = readExact(inputStream, 12);
            if (!asciiEquals(riffHeader, 0, "RIFF")
                    || !asciiEquals(riffHeader, 8, "WEBP")
                    || littleEndianUnsignedInt(riffHeader, 4) + 8L != file.getSize()) {
                throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
            }

            long remaining = file.getSize() - 12L;
            ImageDimensions canvasDimensions = null;
            ImageDimensions imageDimensions = null;
            while (remaining >= 8L) {
                byte[] chunkHeader = readExact(inputStream, 8);
                remaining -= 8L;
                long chunkSize = littleEndianUnsignedInt(chunkHeader, 4);
                if (chunkSize > remaining) {
                    throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
                }

                int prefixLength = (int) Math.min(chunkSize, 10L);
                byte[] chunkPrefix = readExact(inputStream, prefixLength);
                skipFully(inputStream, chunkSize - prefixLength);
                remaining -= chunkSize;

                if (asciiEquals(chunkHeader, 0, "VP8X")) {
                    if (chunkSize != 10L) {
                        throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
                    }
                    canvasDimensions = new ImageDimensions(
                            1 + littleEndian24(chunkPrefix, 4),
                            1 + littleEndian24(chunkPrefix, 7)
                    );
                } else if (asciiEquals(chunkHeader, 0, "VP8L")) {
                    imageDimensions = readVp8lDimensions(chunkPrefix, chunkSize);
                } else if (asciiEquals(chunkHeader, 0, "VP8 ")) {
                    imageDimensions = readVp8Dimensions(chunkPrefix, chunkSize);
                }

                if ((chunkSize & 1L) == 1L) {
                    if (remaining < 1L || inputStream.read() < 0) {
                        throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
                    }
                    remaining--;
                }
            }
            if (remaining != 0L || imageDimensions == null) {
                throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
            }
            return canvasDimensions == null ? imageDimensions : canvasDimensions;
        } catch (MyException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
    }

    private ImageDimensions readVp8lDimensions(byte[] chunkPrefix, long chunkSize) {
        if (chunkSize < 5L || chunkPrefix.length < 5 || unsigned(chunkPrefix[0]) != 0x2f) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
        int b1 = unsigned(chunkPrefix[1]);
        int b2 = unsigned(chunkPrefix[2]);
        int b3 = unsigned(chunkPrefix[3]);
        int b4 = unsigned(chunkPrefix[4]);
        int width = 1 + b1 + ((b2 & 0x3f) << 8);
        int height = 1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
        return new ImageDimensions(width, height);
    }

    private ImageDimensions readVp8Dimensions(byte[] chunkPrefix, long chunkSize) {
        if (chunkSize < 10L || chunkPrefix.length < 10
                || unsigned(chunkPrefix[3]) != 0x9d
                || unsigned(chunkPrefix[4]) != 0x01
                || unsigned(chunkPrefix[5]) != 0x2a) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
        int width = littleEndian16(chunkPrefix, 6) & 0x3fff;
        int height = littleEndian16(chunkPrefix, 8) & 0x3fff;
        return new ImageDimensions(width, height);
    }

    private byte[] readExact(InputStream inputStream, int length) throws IOException {
        byte[] bytes = inputStream.readNBytes(length);
        if (bytes.length != length) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
        return bytes;
    }

    private void skipFully(InputStream inputStream, long length) throws IOException {
        long remaining = length;
        while (remaining > 0L) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
            } else if (inputStream.read() < 0) {
                throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
            } else {
                remaining--;
            }
        }
    }

    private void validateDimensions(ImageDimensions dimensions) {
        long pixels = (long) dimensions.width() * dimensions.height();
        if (dimensions.width() <= 0 || dimensions.height() <= 0
                || dimensions.width() > properties.getMaxWidth()
                || dimensions.height() > properties.getMaxHeight()
                || pixels > properties.getMaxPixels()) {
            throw new MyException(ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean asciiEquals(byte[] source, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (source.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (source[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private int littleEndian16(byte[] source, int offset) {
        return unsigned(source[offset]) | (unsigned(source[offset + 1]) << 8);
    }

    private int littleEndian24(byte[] source, int offset) {
        return unsigned(source[offset])
                | (unsigned(source[offset + 1]) << 8)
                | (unsigned(source[offset + 2]) << 16);
    }

    private long littleEndianUnsignedInt(byte[] source, int offset) {
        return Integer.toUnsignedLong(unsigned(source[offset])
                | (unsigned(source[offset + 1]) << 8)
                | (unsigned(source[offset + 2]) << 16)
                | (unsigned(source[offset + 3]) << 24));
    }

    private static final class ImageDimensions {

        private final int width;
        private final int height;

        private ImageDimensions(int width, int height) {
            this.width = width;
            this.height = height;
        }

        private int width() {
            return width;
        }

        private int height() {
            return height;
        }
    }
}
