package com.document.parsing.ocr;

import com.document.parsing.core.exception.OcrUnavailableException;
import com.document.parsing.core.ocr.OcrService;
import com.document.parsing.core.parser.ParseOptions;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class TesseractOcrService implements OcrService {

    @Override
    public String extractText(byte[] imageData, String mimeType, ParseOptions options) {
        if (imageData == null || imageData.length == 0) {
            return "";
        }

        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (bufferedImage == null) {
                return "";
            }

            ITesseract tesseract = new Tesseract();
            if (options.getOcrDataPath() != null && !options.getOcrDataPath().isBlank()) {
                tesseract.setDatapath(options.getOcrDataPath());
            }
            return tesseract.doOCR(bufferedImage);
        } catch (IOException | TesseractException e) {
            throw new OcrUnavailableException("OCR execution failed", e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            new Tesseract();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
