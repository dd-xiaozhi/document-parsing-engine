package com.document.parsing.pdf;

import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfDocumentParserTest {

    @Test
    void shouldParseSimplePdfText() throws Exception {
        byte[] pdfBytes = createPdf("This is a PDF content with enough text to avoid OCR fallback in test.");

        PdfDocumentParser parser = new PdfDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(pdfBytes))
            .fileName("sample.pdf")
            .hintedType(DocumentType.PDF)
            .build();

        Document document = parser.parse(request).getDocument();
        assertThat(document.getRawText()).contains("This is a PDF content");
        assertThat(document.getPages()).hasSize(1);
    }

    @Test
    void shouldExtractTableByHeuristic() throws Exception {
        String tableLike = String.join(System.lineSeparator(),
            "ID|Name|Score",
            "1|Alice|99",
            "2|Bob|87"
        );
        byte[] pdfBytes = createPdf(tableLike);

        PdfDocumentParser parser = new PdfDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(pdfBytes))
            .fileName("table.pdf")
            .hintedType(DocumentType.PDF)
            .build();

        Document document = parser.parse(request).getDocument();
        assertThat(document.getTables()).hasSize(1);
        assertThat(document.getTables().get(0).getRows()).hasSize(3);
    }

    @Test
    void shouldStreamParsePdfIntoEvents() throws Exception {
        byte[] pdfBytes = createPdf("Streaming parser line");
        PdfDocumentParser parser = new PdfDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(pdfBytes))
            .fileName("stream.pdf")
            .hintedType(DocumentType.PDF)
            .options(ParseOptions.builder().enableOcr(false).build())
            .build();

        List<BlockEventType> types = parser.parseStream(request).orElseThrow()
            .map(event -> event.getType())
            .toList();

        assertThat(types).contains(BlockEventType.PAGE_START, BlockEventType.BLOCK, BlockEventType.PAGE_END);
        assertThat(types).doesNotContain(BlockEventType.DONE);
    }

    private byte[] createPdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                String[] lines = text.split("\\R");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        stream.newLineAtOffset(0, -16);
                    }
                    stream.showText(lines[i]);
                }
                stream.endText();
            }

            doc.save(output);
            return output.toByteArray();
        }
    }
}
