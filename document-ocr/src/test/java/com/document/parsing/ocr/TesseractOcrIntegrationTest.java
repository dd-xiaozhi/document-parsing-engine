package com.document.parsing.ocr;

import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_OCR_IT", matches = "(?i)1|true|yes")
@EnabledIfEnvironmentVariable(named = "TESSDATA_PREFIX", matches = ".+")
class TesseractOcrIntegrationTest {

    @Test
    void shouldExtractTextWithRealTessdata() throws Exception {
        TesseractOcrService service = new TesseractOcrService();
        Assumptions.assumeTrue(service.isAvailable(), "Tesseract runtime is not available");

        byte[] image = createImage("OCR TEST");
        ParseOptions options = ParseOptions.builder()
            .enableOcr(true)
            .failOnOcrError(true)
            .ocrDataPath(System.getenv("TESSDATA_PREFIX"))
            .build();

        String text = service.extractText(image, "image/png", options);
        assertThat(text).isNotBlank();
    }

    @Test
    void imageParserShouldPopulateRawTextWhenOcrEnabled() throws Exception {
        ImageDocumentParser parser = new ImageDocumentParser();
        byte[] image = createImage("HELLO 2026");

        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream(image))
            .fileName("ocr-it.png")
            .hintedType(DocumentType.IMAGE)
            .options(ParseOptions.builder()
                .enableOcr(true)
                .failOnOcrError(true)
                .ocrDataPath(System.getenv("TESSDATA_PREFIX"))
                .build())
            .build();

        Document document = parser.parse(request).getDocument();
        assertThat(document.getRawText()).isNotBlank();
    }

    private byte[] createImage(String text) throws Exception {
        BufferedImage image = new BufferedImage(800, 220, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font("SansSerif", Font.BOLD, 72));
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.drawString(text, 40, 140);
        g.dispose();

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        }
    }
}
