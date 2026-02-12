package com.document.parsing.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Document {
    private Metadata metadata;
    private List<Page> pages;
    private List<Table> tables;
    private List<ImageElement> images;
    private String rawText;
    private List<ParseWarning> warnings;
    private Map<String, Object> extensions;

    private Document(Builder builder) {
        this.metadata = builder.metadata;
        this.pages = builder.pages;
        this.tables = builder.tables;
        this.images = builder.images;
        this.rawText = builder.rawText;
        this.warnings = builder.warnings;
        this.extensions = builder.extensions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
    }

    public List<ImageElement> getImages() {
        return images;
    }

    public void setImages(List<ImageElement> images) {
        this.images = images;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public List<ParseWarning> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<ParseWarning> warnings) {
        this.warnings = warnings;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions;
    }

    public static final class Builder {
        private Metadata metadata = new Metadata();
        private List<Page> pages = new ArrayList<>();
        private List<Table> tables = new ArrayList<>();
        private List<ImageElement> images = new ArrayList<>();
        private String rawText = "";
        private List<ParseWarning> warnings = new ArrayList<>();
        private Map<String, Object> extensions = new HashMap<>();

        public Builder metadata(Metadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder pages(List<Page> pages) {
            this.pages = new ArrayList<>(pages);
            return this;
        }

        public Builder tables(List<Table> tables) {
            this.tables = new ArrayList<>(tables);
            return this;
        }

        public Builder images(List<ImageElement> images) {
            this.images = new ArrayList<>(images);
            return this;
        }

        public Builder rawText(String rawText) {
            this.rawText = rawText;
            return this;
        }

        public Builder warnings(List<ParseWarning> warnings) {
            this.warnings = new ArrayList<>(warnings);
            return this;
        }

        public Builder extensions(Map<String, Object> extensions) {
            this.extensions = new HashMap<>(extensions);
            return this;
        }

        public Document build() {
            return new Document(this);
        }
    }
}
