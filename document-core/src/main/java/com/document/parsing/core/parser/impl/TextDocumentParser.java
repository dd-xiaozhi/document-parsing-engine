package com.document.parsing.core.parser.impl;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.ParseException;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.Metadata;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.TextBlock;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.TXT;
    }

    @Override
    public ParseResult parse(ParseRequest request) throws ParseException {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getStream(), request.getOptions().getCharset()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append(System.lineSeparator());
                }
            }

            String content = sb.toString().trim();
            Page page = new Page(1, List.of(new TextBlock(content)));
            Metadata metadata = new Metadata();
            metadata.setPageCount(1);

            Document document = Document.builder()
                .metadata(metadata)
                .pages(List.of(page))
                .rawText(content)
                .build();
            return ParseResult.of(document);
        } catch (IOException e) {
            throw new ParseException("Failed to parse TXT document", e);
        }
    }

    @Override
    public int getPriority() {
        return 50;
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(request.getStream(), request.getOptions().getCharset()));

        Stream<BlockEvent> contentEvents = reader.lines()
            .filter(line -> !line.isBlank())
            .map(line -> BlockEvent.block(1, new TextBlock(line)));

        Stream<BlockEvent> stream = Stream.concat(
                Stream.of(BlockEvent.pageStart(1)),
                Stream.concat(contentEvents, Stream.of(BlockEvent.pageEnd(1)))
            )
            .onClose(() -> closeQuietly(reader));
        return Optional.of(stream);
    }

    private void closeQuietly(BufferedReader reader) {
        try {
            reader.close();
        } catch (IOException ignored) {
            // Ignore close exceptions for streaming.
        }
    }
}
