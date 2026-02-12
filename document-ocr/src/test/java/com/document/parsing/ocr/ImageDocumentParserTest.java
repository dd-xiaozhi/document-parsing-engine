package com.document.parsing.ocr;

import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ImageDocumentParserTest {

    @Test
    void shouldParseImageWhenOcrDisabled() throws Exception {
        byte[] png = createImage();
        ImageDocumentParser parser = new ImageDocumentParser();

        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(png))
            .fileName("sample.png")
            .hintedType(DocumentType.IMAGE)
            .options(ParseOptions.builder().enableOcr(false).build())
            .build();

        Document document = parser.parse(request).getDocument();

        assertThat(document.getImages()).hasSize(1);
        assertThat(document.getRawText()).isBlank();
    }

    private byte[] createImage() throws Exception {
        BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 120, 40);
        g.setColor(Color.BLACK);
        g.drawString("TEST", 10, 20);
        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
