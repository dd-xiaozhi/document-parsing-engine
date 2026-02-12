package com.document.parsing.core.ocr;

import com.document.parsing.core.exception.ParseException;
import com.document.parsing.core.parser.ParseOptions;

public interface OcrService {

    String extractText(byte[] imageData, String mimeType, ParseOptions options) throws ParseException;

    default boolean isAvailable() {
        return true;
    }

    default int getPriority() {
        return 100;
    }
}
