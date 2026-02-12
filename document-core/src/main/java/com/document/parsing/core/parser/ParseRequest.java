package com.document.parsing.core.parser;

import com.document.parsing.core.model.DocumentType;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Objects;

public class ParseRequest {
    private final InputStream stream;
    private final String fileName;
    private final long size;
    private final DocumentType hintedType;
    private final ParseOptions options;

    private ParseRequest(Builder builder) {
        InputStream rawStream = Objects.requireNonNull(builder.stream, "stream must not be null");
        this.stream = rawStream.markSupported() ? rawStream : new BufferedInputStream(rawStream);
        this.fileName = builder.fileName;
        this.size = builder.size;
        this.hintedType = builder.hintedType;
        this.options = builder.options == null ? ParseOptions.defaultOptions() : builder.options;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
            .stream(stream)
            .fileName(fileName)
            .size(size)
            .hintedType(hintedType)
            .options(options);
    }

    public InputStream getStream() {
        return stream;
    }

    public String getFileName() {
        return fileName;
    }

    public long getSize() {
        return size;
    }

    public DocumentType getHintedType() {
        return hintedType;
    }

    public ParseOptions getOptions() {
        return options;
    }

    public static final class Builder {
        private InputStream stream;
        private String fileName;
        private long size = -1L;
        private DocumentType hintedType = DocumentType.UNKNOWN;
        private ParseOptions options = ParseOptions.defaultOptions();

        public Builder stream(InputStream stream) {
            this.stream = stream;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder hintedType(DocumentType hintedType) {
            this.hintedType = hintedType == null ? DocumentType.UNKNOWN : hintedType;
            return this;
        }

        public Builder options(ParseOptions options) {
            this.options = options;
            return this;
        }

        public ParseRequest build() {
            return new ParseRequest(this);
        }
    }
}
