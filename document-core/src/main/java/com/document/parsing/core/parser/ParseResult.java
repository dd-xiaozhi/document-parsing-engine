package com.document.parsing.core.parser;

import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.ParseWarning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ParseResult {
    private final Document document;
    private final List<ParseWarning> warnings;

    public ParseResult(Document document, List<ParseWarning> warnings) {
        this.document = Objects.requireNonNull(document, "document must not be null");
        this.warnings = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(warnings, List.of())));
    }

    public static ParseResult of(Document document) {
        return new ParseResult(document, List.of());
    }

    public Document getDocument() {
        return document;
    }

    public List<ParseWarning> getWarnings() {
        return warnings;
    }
}
