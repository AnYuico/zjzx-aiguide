package com.tzp.zjzx.manager.upload;

public final class ValidatedImage {

    private final ImageFileType type;
    private final int width;
    private final int height;

    public ValidatedImage(ImageFileType type, int width, int height) {
        this.type = type;
        this.width = width;
        this.height = height;
    }

    public ImageFileType getType() {
        return type;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
