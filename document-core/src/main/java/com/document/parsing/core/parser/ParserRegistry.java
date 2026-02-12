package com.document.parsing.core.parser;

import com.document.parsing.core.model.DocumentType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class ParserRegistry {
    private final List<DocumentParser> parsers = new CopyOnWriteArrayList<>();

    public void register(DocumentParser parser) {
        parsers.add(parser);
    }

    public void registerAll(List<DocumentParser> parserList) {
        parsers.addAll(parserList);
    }

    public List<DocumentParser> getParsers() {
        return List.copyOf(parsers);
    }

    public Optional<DocumentParser> findParser(DocumentType type) {
        return parsers.stream()
            .filter(p -> p.supports(type))
            .sorted(Comparator.comparingInt(DocumentParser::getPriority)
                .thenComparing(p -> p.getClass().getName()))
            .findFirst();
    }

    public List<DocumentParser> findParsers(DocumentType type) {
        List<DocumentParser> matches = new ArrayList<>();
        for (DocumentParser parser : parsers) {
            if (parser.supports(type)) {
                matches.add(parser);
            }
        }
        matches.sort(Comparator.comparingInt(DocumentParser::getPriority)
            .thenComparing(p -> p.getClass().getName()));
        return matches;
    }
}
