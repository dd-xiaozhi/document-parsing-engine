package com.document.parsing.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "document.engine")
public class DocumentEngineProperties {
    private final Async async = new Async();
    private final Ocr ocr = new Ocr();
    private final Parser parser = new Parser();
    private final Pipeline pipeline = new Pipeline();

    public Async getAsync() {
        return async;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public Parser getParser() {
        return parser;
    }

    public Pipeline getPipeline() {
        return pipeline;
    }

    public static class Async {
        private int corePoolSize = 4;
        private int maxPoolSize = 16;
        private int queueCapacity = 200;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class Ocr {
        private boolean enabled = true;
        private boolean failOnError = false;
        private String dataPath;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isFailOnError() {
            return failOnError;
        }

        public void setFailOnError(boolean failOnError) {
            this.failOnError = failOnError;
        }

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }
    }

    public static class Parser {
        private int maxPages = -1;
        private Charset charset = StandardCharsets.UTF_8;
        private int lowTextDensityThreshold = 40;

        public int getMaxPages() {
            return maxPages;
        }

        public void setMaxPages(int maxPages) {
            this.maxPages = maxPages;
        }

        public Charset getCharset() {
            return charset;
        }

        public void setCharset(Charset charset) {
            this.charset = charset;
        }

        public int getLowTextDensityThreshold() {
            return lowTextDensityThreshold;
        }

        public void setLowTextDensityThreshold(int lowTextDensityThreshold) {
            this.lowTextDensityThreshold = lowTextDensityThreshold;
        }
    }

    public static class Pipeline {
        private boolean watermarkCleanupEnabled = false;

        public boolean isWatermarkCleanupEnabled() {
            return watermarkCleanupEnabled;
        }

        public void setWatermarkCleanupEnabled(boolean watermarkCleanupEnabled) {
            this.watermarkCleanupEnabled = watermarkCleanupEnabled;
        }
    }
}
