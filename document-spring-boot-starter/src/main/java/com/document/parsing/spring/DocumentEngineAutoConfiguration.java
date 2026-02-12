package com.document.parsing.spring;

import com.document.parsing.core.engine.DocumentEngine;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.pipeline.DocumentProcessor;
import com.document.parsing.core.pipeline.WatermarkCleanupProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@AutoConfiguration
@ConditionalOnClass(DocumentEngine.class)
@EnableConfigurationProperties(DocumentEngineProperties.class)
public class DocumentEngineAutoConfiguration {

    @Bean(name = "documentEngineExecutor")
    @ConditionalOnMissingBean(name = "documentEngineExecutor")
    public ExecutorService documentEngineExecutor(DocumentEngineProperties properties) {
        return new ThreadPoolExecutor(
            properties.getAsync().getCorePoolSize(),
            properties.getAsync().getMaxPoolSize(),
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(properties.getAsync().getQueueCapacity()),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public DocumentEngine documentEngine(ObjectProvider<DocumentParser> parserProvider,
                                         ObjectProvider<DocumentProcessor> processorProvider,
                                         @Qualifier("documentEngineExecutor") ExecutorService executor,
                                         DocumentEngineProperties properties) {
        ParseOptions options = ParseOptions.builder()
            .enableOcr(properties.getOcr().isEnabled())
            .failOnOcrError(properties.getOcr().isFailOnError())
            .ocrDataPath(properties.getOcr().getDataPath())
            .maxPages(properties.getParser().getMaxPages())
            .charset(properties.getParser().getCharset())
            .lowTextDensityThreshold(properties.getParser().getLowTextDensityThreshold())
            .watermarkCleanupEnabled(properties.getPipeline().isWatermarkCleanupEnabled())
            .build();

        DocumentEngine.Builder builder = DocumentEngine.builder()
            .autoRegister()
            .defaultOptions(options)
            .executorService(executor, false)
            .asyncPool(
                properties.getAsync().getCorePoolSize(),
                properties.getAsync().getMaxPoolSize(),
                properties.getAsync().getQueueCapacity()
            );

        parserProvider.orderedStream().forEach(builder::register);
        processorProvider.orderedStream().forEach(builder::addProcessor);

        if (properties.getPipeline().isWatermarkCleanupEnabled()) {
            builder.addProcessor(new WatermarkCleanupProcessor());
        }

        return builder.build();
    }
}
