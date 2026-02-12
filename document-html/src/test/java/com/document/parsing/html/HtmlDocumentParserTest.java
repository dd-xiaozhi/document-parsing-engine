package com.document.parsing.html;

import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlDocumentParserTest {

    @Test
    void shouldParseHtml() {
        String html = "<html><head><title>T</title></head><body><h1>Hello</h1><table><tr><td>A</td></tr></table></body></html>";

        HtmlDocumentParser parser = new HtmlDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(html.getBytes()))
            .fileName("sample.html")
            .hintedType(DocumentType.HTML)
            .build();

        Document document = parser.parse(request).getDocument();
        assertThat(document.getRawText()).contains("Hello");
        assertThat(document.getTables()).hasSize(1);
    }

    @Test
    void shouldStreamHtmlEvents() {
        String html = "<html><body><p>Hello</p><table><tr><td>A</td></tr></table><img src='a.png' /></body></html>";
        HtmlDocumentParser parser = new HtmlDocumentParser();

        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(html.getBytes()))
            .fileName("stream.html")
            .hintedType(DocumentType.HTML)
            .build();

        List<BlockEventType> types = parser.parseStream(request).orElseThrow()
            .map(event -> event.getType())
            .toList();

        assertThat(types).containsExactly(
            BlockEventType.PAGE_START,
            BlockEventType.BLOCK,
            BlockEventType.BLOCK,
            BlockEventType.BLOCK,
            BlockEventType.PAGE_END
        );
    }
}
