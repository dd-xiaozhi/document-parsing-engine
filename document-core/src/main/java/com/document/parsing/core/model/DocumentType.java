package com.document.parsing.core.model;

import java.util.Locale;

public enum DocumentType {
    PDF,
    DOCX,
    XLSX,
    PPTX,
    TXT,
    MARKDOWN,
    HTML,
    IMAGE,
    UNKNOWN;

    public static DocumentType fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }

        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return UNKNOWN;
        }

        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "pdf" -> PDF;
            case "docx" -> DOCX;
            case "xlsx" -> XLSX;
            case "pptx" -> PPTX;
            case "txt" -> TXT;
            case "md", "markdown" -> MARKDOWN;
            case "html", "htm" -> HTML;
            case "png", "jpg", "jpeg", "bmp", "tif", "tiff", "gif", "webp" -> IMAGE;
            default -> UNKNOWN;
        };
    }
}
