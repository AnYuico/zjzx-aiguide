package com.tzp.zjzx.manager.upload;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.properties.ImageUploadProperties;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageUploadValidatorTest {

    private ImageUploadProperties properties;
    private ImageUploadValidator validator;

    @BeforeEach
    void setUp() {
        properties = new ImageUploadProperties();
        properties.setMaxSize(DataSize.ofMegabytes(1));
        properties.setMaxWidth(100);
        properties.setMaxHeight(100);
        properties.setMaxPixels(10_000L);
        validator = new ImageUploadValidator(properties);
    }

    @Test
    void detectsJpegFromContentInsteadOfOriginalFilenameOrDeclaredMime() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "attack.html", "text/html", createImage("jpg", 3, 2));

        ValidatedImage result = validator.validate(file);

        assertEquals(ImageFileType.JPEG, result.getType());
        assertEquals(3, result.getWidth());
        assertEquals(2, result.getHeight());
    }

    @Test
    void acceptsPngAndStructurallyValidWebp() throws IOException {
        ValidatedImage png = validator.validate(new MockMultipartFile(
                "file", "image.png", "image/png", createImage("png", 4, 5)));
        ValidatedImage webp = validator.validate(new MockMultipartFile(
                "file", "image.webp", "image/webp", createSimpleVp8Webp(6, 7)));

        assertEquals(ImageFileType.PNG, png.getType());
        assertEquals(ImageFileType.WEBP, webp.getType());
        assertEquals(6, webp.getWidth());
        assertEquals(7, webp.getHeight());
    }

    @Test
    void rejectsHtmlSvgAndFakePng() {
        assertRejected(
                new MockMultipartFile("file", "a.jpg", "image/jpeg",
                        "<html>bad</html>".getBytes(StandardCharsets.UTF_8)),
                ResultCodeEnum.UPLOAD_FILE_TYPE_NOT_ALLOWED);
        assertRejected(
                new MockMultipartFile("file", "a.svg", "image/svg+xml",
                        "<svg xmlns='http://www.w3.org/2000/svg'></svg>"
                                .getBytes(StandardCharsets.UTF_8)),
                ResultCodeEnum.UPLOAD_FILE_TYPE_NOT_ALLOWED);

        byte[] fakePng = new byte[32];
        byte[] signature = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        System.arraycopy(signature, 0, fakePng, 0, signature.length);
        assertRejected(
                new MockMultipartFile("file", "a.png", "image/png", fakePng),
                ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
    }

    @Test
    void rejectsEmptyOversizedAndExcessiveDimensions() throws IOException {
        assertRejected(
                new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]),
                ResultCodeEnum.UPLOAD_FILE_EMPTY);

        properties.setMaxSize(DataSize.ofBytes(8));
        assertRejected(
                new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[9]),
                ResultCodeEnum.UPLOAD_FILE_TOO_LARGE);

        properties.setMaxSize(DataSize.ofMegabytes(1));
        properties.setMaxWidth(2);
        assertRejected(
                new MockMultipartFile("file", "wide.png", "image/png",
                        createImage("png", 3, 1)),
                ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
    }

    @Test
    void rejectsWebpWithoutImageChunkOrWithTrailingContent() {
        assertRejected(
                new MockMultipartFile("file", "empty.webp", "image/webp", createVp8xOnlyWebp()),
                ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);

        byte[] valid = createSimpleVp8Webp(2, 2);
        byte[] withTrailingContent = new byte[valid.length + 1];
        System.arraycopy(valid, 0, withTrailingContent, 0, valid.length);
        assertRejected(
                new MockMultipartFile("file", "trailing.webp", "image/webp", withTrailingContent),
                ResultCodeEnum.UPLOAD_FILE_CONTENT_INVALID);
    }

    private void assertRejected(MockMultipartFile file, ResultCodeEnum expectedCode) {
        MyException exception = assertThrows(MyException.class, () -> validator.validate(file));
        assertEquals(expectedCode, exception.getResultCodeEnum());
    }

    private byte[] createImage(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] createSimpleVp8Webp(int width, int height) {
        byte[] bytes = new byte[30];
        writeAscii(bytes, 0, "RIFF");
        writeLittleEndian32(bytes, 4, bytes.length - 8);
        writeAscii(bytes, 8, "WEBP");
        writeAscii(bytes, 12, "VP8 ");
        writeLittleEndian32(bytes, 16, 10);
        bytes[23] = (byte) 0x9d;
        bytes[24] = 0x01;
        bytes[25] = 0x2a;
        writeLittleEndian16(bytes, 26, width);
        writeLittleEndian16(bytes, 28, height);
        return bytes;
    }

    private byte[] createVp8xOnlyWebp() {
        byte[] bytes = new byte[30];
        writeAscii(bytes, 0, "RIFF");
        writeLittleEndian32(bytes, 4, bytes.length - 8);
        writeAscii(bytes, 8, "WEBP");
        writeAscii(bytes, 12, "VP8X");
        writeLittleEndian32(bytes, 16, 10);
        return bytes;
    }

    private void writeAscii(byte[] target, int offset, String value) {
        byte[] source = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(source, 0, target, offset, source.length);
    }

    private void writeLittleEndian16(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
    }

    private void writeLittleEndian32(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
        target[offset + 3] = (byte) (value >> 24);
    }
}
