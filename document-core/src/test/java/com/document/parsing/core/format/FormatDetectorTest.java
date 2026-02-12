package com.document.parsing.core.format;

import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FormatDetectorTest {
    private final FormatDetector detector = new FormatDetector();

    @Test
    void shouldDetectByFileName() {
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream("hello".getBytes()))
            .fileName("sample.md")
            .build();

        assertThat(detector.detect(request)).isEqualTo(DocumentType.MARKDOWN);
    }

    @Test
    void shouldDetectPdfByMagic() {
        ParseRequest request = ParseRequest.builder()
            .stream(new ByteArrayInputStream("%PDF-1.7 test".getBytes()))
            .fileName("unknown.bin")
            .build();

        assertThat(detector.detect(request)).isEqualTo(DocumentType.PDF);
    }
}
