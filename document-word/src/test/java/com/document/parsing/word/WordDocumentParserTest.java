package com.document.parsing.word;

import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WordDocumentParserTest {

    @Test
    void shouldParseDocxTextAndTable() throws Exception {
        byte[] docx = createDocx();

        WordDocumentParser parser = new WordDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(docx))
            .fileName("sample.docx")
            .hintedType(DocumentType.DOCX)
            .build();

        Document document = parser.parse(request).getDocument();

        assertThat(document.getRawText()).contains("Hello DOCX");
        assertThat(document.getTables()).hasSize(1);
    }

    @Test
    void shouldStreamDocxEvents() throws Exception {
        byte[] docx = createDocx();
        WordDocumentParser parser = new WordDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(docx))
            .fileName("stream.docx")
            .hintedType(DocumentType.DOCX)
            .build();

        List<BlockEventType> types = parser.parseStream(request).orElseThrow()
            .map(event -> event.getType())
            .toList();

        assertThat(types).contains(BlockEventType.PAGE_START, BlockEventType.BLOCK, BlockEventType.PAGE_END);
    }

    private byte[] createDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.createParagraph().createRun().setText("Hello DOCX");
            XWPFTable table = doc.createTable(1, 2);
            table.getRow(0).getCell(0).setText("A1");
            table.getRow(0).getCell(1).setText("B1");
            doc.write(out);
            return out.toByteArray();
        }
    }
}
