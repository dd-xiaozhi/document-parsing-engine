package com.document.parsing.spring;

import com.document.parsing.core.engine.DocumentEngine;
import com.document.parsing.core.model.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEngineAutoConfigurationTest {
    @TempDir
    Path tempDir;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(DocumentEngineAutoConfiguration.class));

    @Test
    void shouldAutoConfigureDocumentEngine() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DocumentEngine.class);
            DocumentEngine engine = context.getBean(DocumentEngine.class);

            Document document = engine.parse(new ByteArrayInputStream("hello".getBytes()), "sample.txt");
            assertThat(document.getRawText()).contains("hello");
        });
    }

    @Test
    void shouldSupportBatchParsingFromAutoConfiguredEngine() throws Exception {
        Path first = Files.writeString(tempDir.resolve("a.md"), "# first");
        Path second = Files.writeString(tempDir.resolve("b.md"), "# second");

        contextRunner.run(context -> {
            DocumentEngine engine = context.getBean(DocumentEngine.class);

            List<Document> syncResult = engine.parseBatch(List.of(first.toFile(), second.toFile()));
            assertThat(syncResult).hasSize(2);
            assertThat(syncResult.get(0).getRawText()).contains("first");
            assertThat(syncResult.get(1).getRawText()).contains("second");

            List<Document> asyncResult = engine.parseBatchAsync(List.of(first.toFile(), second.toFile())).join();
            assertThat(asyncResult).hasSize(2);
            assertThat(asyncResult.get(0).getRawText()).contains("first");
            assertThat(asyncResult.get(1).getRawText()).contains("second");
        });
    }
}
