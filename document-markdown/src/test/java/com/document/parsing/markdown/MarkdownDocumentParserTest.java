package com.document.parsing.markdown;

import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocumentParserTest {

    @Test
    void shouldParseMarkdown() {
        String markdown = "# Title\n\nHello **Markdown**";
        MarkdownDocumentParser parser = new MarkdownDocumentParser();

        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(markdown.getBytes()))
            .fileName("sample.md")
            .hintedType(DocumentType.MARKDOWN)
            .build();

        Document document = parser.parse(request).getDocument();

        assertThat(document.getRawText()).contains("Title");
        assertThat(document.getRawText()).contains("Hello Markdown");
    }

    @Test
    void shouldStreamMarkdownEvents() {
        String markdown = "# Title\n\nHello **Markdown**";
        MarkdownDocumentParser parser = new MarkdownDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(markdown.getBytes()))
            .fileName("stream.md")
            .hintedType(DocumentType.MARKDOWN)
            .build();

        List<BlockEventType> types = parser.parseStream(request).orElseThrow()
            .map(event -> event.getType())
            .toList();

        assertThat(types).containsExactly(
            BlockEventType.PAGE_START,
            BlockEventType.BLOCK,
            BlockEventType.PAGE_END
        );
    }
}
