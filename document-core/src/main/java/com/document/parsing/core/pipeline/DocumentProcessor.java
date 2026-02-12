package com.document.parsing.core.pipeline;

import com.document.parsing.core.model.Document;

public interface DocumentProcessor {

    default int getOrder() {
        return 1000;
    }

    Document process(Document document, ProcessingContext context);
}
