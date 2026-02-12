package com.document.parsing.core.parser;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.exception.ParseException;

import java.util.Optional;
import java.util.stream.Stream;

public interface DocumentParser {

    boolean supports(DocumentType type);

    ParseResult parse(ParseRequest request) throws ParseException;

    default Optional<Stream<BlockEvent>> parseStream(ParseRequest request) throws ParseException {
        return Optional.empty();
    }

    default int getPriority() {
        return 100;
    }
}
