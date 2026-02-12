package com.document.parsing.excel;

import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelDocumentParserTest {

    @Test
    void shouldParseXlsxInStreamingMode() throws Exception {
        byte[] xlsx = createWorkbook();

        ExcelDocumentParser parser = new ExcelDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(xlsx))
            .fileName("sample.xlsx")
            .hintedType(DocumentType.XLSX)
            .build();

        Document document = parser.parse(request).getDocument();

        assertThat(document.getTables()).hasSize(1);
        assertThat(document.getRawText()).contains("hello");
        assertThat(document.getMetadata().getSheetCount()).isEqualTo(1);
    }

    @Test
    void shouldEmitStreamEventsForXlsx() throws Exception {
        byte[] xlsx = createWorkbook();
        ExcelDocumentParser parser = new ExcelDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(xlsx))
            .fileName("sample.xlsx")
            .hintedType(DocumentType.XLSX)
            .build();

        List<BlockEventType> types = parser.parseStream(request).orElseThrow()
            .map(event -> event.getType())
            .toList();

        assertThat(types).containsExactly(
            BlockEventType.PAGE_START,
            BlockEventType.BLOCK,
            BlockEventType.BLOCK,
            BlockEventType.PAGE_END
        );
    }

    private byte[] createWorkbook() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = wb.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("hello");
            sheet.getRow(0).createCell(1).setCellValue("world");
            wb.write(out);
            return out.toByteArray();
        }
    }
}
