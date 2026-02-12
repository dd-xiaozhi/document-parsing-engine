package com.document.parsing.word;

import com.document.parsing.core.event.BlockEventType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PptxDocumentParserTest {

    @Test
    void shouldParsePptxTextTableAndImage() throws Exception {
        byte[] pptx = createPptx();
        PptxDocumentParser parser = new PptxDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(pptx))
            .fileName("sample.pptx")
            .hintedType(DocumentType.PPTX)
            .build();

        Document document = parser.parse(request).getDocument();

        assertThat(document.getPages()).hasSize(2);
        assertThat(document.getRawText()).contains("Hello PPTX").contains("Second slide");
        assertThat(document.getTables()).hasSize(1);
        assertThat(document.getImages()).hasSize(1);
    }

    @Test
    void shouldRespectMaxPagesForPptx() throws Exception {
        byte[] pptx = createPptx();
        PptxDocumentParser parser = new PptxDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(pptx))
            .fileName("limit.pptx")
            .hintedType(DocumentType.PPTX)
            .options(ParseOptions.builder().maxPages(1).build())
            .build();

        Document document = parser.parse(request).getDocument();

        assertThat(document.getPages()).hasSize(1);
        assertThat(document.getRawText()).contains("Hello PPTX");
        assertThat(document.getRawText()).doesNotContain("Second slide");
    }

    @Test
    void shouldStreamPptxEvents() throws Exception {
        byte[] pptx = createPptx();
        PptxDocumentParser parser = new PptxDocumentParser();
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(pptx))
            .fileName("stream.pptx")
            .hintedType(DocumentType.PPTX)
            .build();

        List<BlockEventType> eventTypes = parser.parseStream(request).orElseThrow()
            .map(event -> event.getType())
            .toList();

        assertThat(eventTypes).contains(BlockEventType.PAGE_START, BlockEventType.BLOCK, BlockEventType.PAGE_END);
    }

    private byte[] createPptx() throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide firstSlide = slideShow.createSlide();

            XSLFTextBox firstText = firstSlide.createTextBox();
            firstText.setAnchor(new Rectangle(40, 40, 480, 60));
            firstText.setText("Hello PPTX");

            XSLFTable table = firstSlide.createTable();
            table.setAnchor(new Rectangle(40, 120, 400, 120));
            XSLFTableRow row1 = table.addRow();
            row1.addCell().setText("A1");
            row1.addCell().setText("B1");
            XSLFTableRow row2 = table.addRow();
            row2.addCell().setText("A2");
            row2.addCell().setText("B2");

            XSLFPictureData pictureData = slideShow.addPicture(createPng(), PictureData.PictureType.PNG);
            XSLFPictureShape pictureShape = firstSlide.createPicture(pictureData);
            pictureShape.setAnchor(new Rectangle(40, 260, 40, 40));

            XSLFSlide secondSlide = slideShow.createSlide();
            XSLFTextBox secondText = secondSlide.createTextBox();
            secondText.setAnchor(new Rectangle(40, 40, 480, 60));
            secondText.setText("Second slide");

            slideShow.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
