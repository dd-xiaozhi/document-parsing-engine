package com.document.parsing.core.format;

import com.document.parsing.core.exception.ParseException;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;

public class FormatDetector {
    private static final int MAGIC_BUFFER_SIZE = 16;

    public DocumentType detect(ParseRequest request) {
        DocumentType hinted = request.getHintedType();
        if (hinted != null && hinted != DocumentType.UNKNOWN) {
            return hinted;
        }

        DocumentType byMagic = detectFromMagic(request.getStream(), DocumentType.UNKNOWN);
        if (byMagic != DocumentType.UNKNOWN) {
            return byMagic;
        }

        DocumentType byName = DocumentType.fromFileName(request.getFileName());
        return byName;
    }

    public DocumentType detect(Path path) {
        try (InputStream in = java.nio.file.Files.newInputStream(path)) {
            DocumentType byMagic = detectFromMagic(in, DocumentType.UNKNOWN);
            if (byMagic != DocumentType.UNKNOWN) {
                return byMagic;
            }
            return DocumentType.fromFileName(path.getFileName().toString());
        } catch (IOException e) {
            throw new ParseException("Failed to detect file format for path: " + path, e);
        }
    }

    private DocumentType detectFromMagic(InputStream in, DocumentType fallback) {
        try {
            if (!in.markSupported()) {
                return fallback;
            }
            in.mark(MAGIC_BUFFER_SIZE);
            byte[] header = in.readNBytes(MAGIC_BUFFER_SIZE);
            in.reset();

            if (startsWith(header, "%PDF-".getBytes())) {
                return DocumentType.PDF;
            }
            if (startsWith(header, new byte[]{(byte) 0x89, 'P', 'N', 'G'})) {
                return DocumentType.IMAGE;
            }
            if (startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
                return DocumentType.IMAGE;
            }
            if (startsWith(header, new byte[]{'G', 'I', 'F'})) {
                return DocumentType.IMAGE;
            }
            if (startsWith(header, new byte[]{'B', 'M'})) {
                return DocumentType.IMAGE;
            }
            if (startsWith(header, new byte[]{'P', 'K', 0x03, 0x04})) {
                return fallback;
            }
            String asText = new String(header).toLowerCase();
            if (asText.contains("<html") || asText.contains("<!doctype html")) {
                return DocumentType.HTML;
            }

            return fallback;
        } catch (IOException e) {
            throw new ParseException("Failed to read stream header for format detection", e);
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOf(source, prefix.length), prefix);
    }
}
