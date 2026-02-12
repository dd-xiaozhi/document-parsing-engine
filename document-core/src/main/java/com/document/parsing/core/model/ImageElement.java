package com.document.parsing.core.model;

public class ImageElement {
    private final String id;
    private final int pageNumber;
    private final String mimeType;
    private final int width;
    private final int height;
    private final byte[] content;

    public ImageElement(String id, int pageNumber, String mimeType, int width, int height, byte[] content) {
        this.id = id;
        this.pageNumber = pageNumber;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.content = content == null ? null : content.clone();
    }

    public String getId() {
        return id;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getMimeType() {
        return mimeType;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getContent() {
        return content == null ? null : content.clone();
    }
}
