package com.document.parsing.core.engine;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.TextBlock;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentEngineTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldParseWithRegisteredParser() {
        DocumentParser parser = new DocumentParser() {
            @Override
            public boolean supports(DocumentType type) {
                return type == DocumentType.MARKDOWN;
            }

            @Override
            public ParseResult parse(ParseRequest request) {
                Document document = Document.builder()
                    .pages(List.of(new Page(1, List.of(new TextBlock("ok")))))
                    .rawText("ok")
                    .build();
                return ParseResult.of(document);
            }
        };

        DocumentEngine engine = DocumentEngine.builder()
            .register(parser)
            .build();

        Document document = engine.parse(new ByteArrayInputStream("# hi".getBytes()), "a.md");
        assertThat(document.getRawText()).isEqualTo("ok");
    }

    @Test
    void shouldAppendDoneEventForStreamParsing() {
        DocumentParser parser = new DocumentParser() {
            @Override
            public boolean supports(DocumentType type) {
                return type == DocumentType.TXT;
            }

            @Override
            public ParseResult parse(ParseRequest request) {
                return ParseResult.of(Document.builder().build());
            }

            @Override
            public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
                return Optional.of(Stream.of(
                    BlockEvent.pageStart(1),
                    BlockEvent.block(1, new TextBlock("line-1")),
                    BlockEvent.pageEnd(1)
                ));
            }
        };

        DocumentEngine engine = DocumentEngine.builder()
            .register(parser)
            .build();

        List<BlockEventType> types = engine.parseStream(new ByteArrayInputStream("x".getBytes()), "a.txt")
            .map(BlockEvent::getType)
            .toList();

        assertThat(types).containsExactly(
            BlockEventType.PAGE_START,
            BlockEventType.BLOCK,
            BlockEventType.PAGE_END,
            BlockEventType.DONE
        );
    }

    @Test
    void shouldParseBatchInInputOrder() throws Exception {
        Path first = Files.writeString(tempDir.resolve("a.md"), "# one");
        Path second = Files.writeString(tempDir.resolve("b.md"), "# two");

        DocumentEngine engine = DocumentEngine.builder()
            .register(markdownEchoParser())
            .build();

        List<Document> documents = engine.parseBatch(List.of(first.toFile(), second.toFile()));

        assertThat(documents)
            .extracting(Document::getRawText)
            .containsExactly("a.md", "b.md");
    }

    @Test
    void shouldParseBatchAsyncInInputOrder() throws Exception {
        Path first = Files.writeString(tempDir.resolve("c.md"), "# three");
        Path second = Files.writeString(tempDir.resolve("d.md"), "# four");

        DocumentEngine engine = DocumentEngine.builder()
            .register(markdownEchoParser())
            .build();

        List<Document> documents = engine.parseBatchAsync(List.of(first.toFile(), second.toFile())).join();

        assertThat(documents)
            .extracting(Document::getRawText)
            .containsExactly("c.md", "d.md");
    }

    private DocumentParser markdownEchoParser() {
        return new DocumentParser() {
            @Override
            public boolean supports(DocumentType type) {
                return type == DocumentType.MARKDOWN;
            }

            @Override
            public ParseResult parse(ParseRequest request) {
                Document document = Document.builder()
                    .pages(List.of(new Page(1, List.of(new TextBlock(request.getFileName())))))
                    .rawText(request.getFileName())
                    .build();
                return ParseResult.of(document);
            }
        };
    }
}
