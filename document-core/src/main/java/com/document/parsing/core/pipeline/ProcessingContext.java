package com.document.parsing.core.pipeline;

import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.parser.ParseRequest;

import java.util.HashMap;
import java.util.Map;

public class ProcessingContext {
    private final ParseRequest request;
    private final DocumentType documentType;
    private final Map<String, Object> attributes = new HashMap<>();

    public ProcessingContext(ParseRequest request, DocumentType documentType) {
        this.request = request;
        this.documentType = documentType;
    }

    public ParseRequest getRequest() {
        return request;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
