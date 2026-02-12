package com.document.parsing.core.parser;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class ParseOptions {
    private final boolean enableOcr;
    private final boolean failOnOcrError;
    private final int maxPages;
    private final Charset charset;
    private final boolean watermarkCleanupEnabled;
    private final int lowTextDensityThreshold;
    private final String ocrDataPath;

    private ParseOptions(Builder builder) {
        this.enableOcr = builder.enableOcr;
        this.failOnOcrError = builder.failOnOcrError;
        this.maxPages = builder.maxPages;
        this.charset = builder.charset;
        this.watermarkCleanupEnabled = builder.watermarkCleanupEnabled;
        this.lowTextDensityThreshold = builder.lowTextDensityThreshold;
        this.ocrDataPath = builder.ocrDataPath;
    }

    public static ParseOptions defaultOptions() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEnableOcr() {
        return enableOcr;
    }

    public boolean isFailOnOcrError() {
        return failOnOcrError;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public Charset getCharset() {
        return charset;
    }

    public boolean isWatermarkCleanupEnabled() {
        return watermarkCleanupEnabled;
    }

    public int getLowTextDensityThreshold() {
        return lowTextDensityThreshold;
    }

    public String getOcrDataPath() {
        return ocrDataPath;
    }

    public static final class Builder {
        private boolean enableOcr = true;
        private boolean failOnOcrError = false;
        private int maxPages = -1;
        private Charset charset = StandardCharsets.UTF_8;
        private boolean watermarkCleanupEnabled = false;
        private int lowTextDensityThreshold = 40;
        private String ocrDataPath;

        public Builder enableOcr(boolean enableOcr) {
            this.enableOcr = enableOcr;
            return this;
        }

        public Builder failOnOcrError(boolean failOnOcrError) {
            this.failOnOcrError = failOnOcrError;
            return this;
        }

        public Builder maxPages(int maxPages) {
            this.maxPages = maxPages;
            return this;
        }

        public Builder charset(Charset charset) {
            this.charset = Objects.requireNonNull(charset, "charset must not be null");
            return this;
        }

        public Builder watermarkCleanupEnabled(boolean watermarkCleanupEnabled) {
            this.watermarkCleanupEnabled = watermarkCleanupEnabled;
            return this;
        }

        public Builder lowTextDensityThreshold(int lowTextDensityThreshold) {
            this.lowTextDensityThreshold = lowTextDensityThreshold;
            return this;
        }

        public Builder ocrDataPath(String ocrDataPath) {
            this.ocrDataPath = ocrDataPath;
            return this;
        }

        public ParseOptions build() {
            return new ParseOptions(this);
        }
    }
}
