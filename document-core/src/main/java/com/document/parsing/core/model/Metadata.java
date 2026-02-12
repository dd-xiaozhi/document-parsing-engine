package com.document.parsing.core.model;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Metadata {
    private String title;
    private String author;
    private String creator;
    private Instant createdAt;
    private Instant modifiedAt;
    private Integer pageCount;
    private Integer sheetCount;
    private final Map<String, Object> customProperties = new HashMap<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCreator() {
        return creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getModifiedAt() {
        return modifiedAt;
    }

    public void setModifiedAt(Instant modifiedAt) {
        this.modifiedAt = modifiedAt;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    public Integer getSheetCount() {
        return sheetCount;
    }

    public void setSheetCount(Integer sheetCount) {
        this.sheetCount = sheetCount;
    }

    public Map<String, Object> getCustomProperties() {
        return customProperties;
    }
}
