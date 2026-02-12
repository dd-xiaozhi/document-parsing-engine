package com.document.parsing.markdown;

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
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MarkdownDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.MARKDOWN;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try {
            String markdown = new String(request.getStream().readAllBytes(), request.getOptions().getCharset());
            Parser parser = Parser.builder().build();
            Node documentNode = parser.parse(markdown);
            String plainText = TextContentRenderer.builder().build().render(documentNode).trim();

            Metadata metadata = new Metadata();
            metadata.setPageCount(1);

            Document document = Document.builder()
                .metadata(metadata)
                .pages(java.util.List.of(new Page(1, java.util.List.of(new TextBlock(plainText)))))
                .rawText(plainText)
                .build();
            return ParseResult.of(document);
        } catch (IOException e) {
            throw new ParseException("Failed to parse Markdown document", e);
        }
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        try {
            String markdown = new String(request.getStream().readAllBytes(), request.getOptions().getCharset());
            Parser parser = Parser.builder().build();
            Node documentNode = parser.parse(markdown);
            String plainText = TextContentRenderer.builder().build().render(documentNode).trim();

            Iterator<BlockEvent> iterator = new Iterator<>() {
                private int stage;

                @Override
                public boolean hasNext() {
                    return stage < 3;
                }

                @Override
                public BlockEvent next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException("No more Markdown block events");
                    }

                    return switch (stage++) {
                        case 0 -> BlockEvent.pageStart(1);
                        case 1 -> BlockEvent.block(1, new TextBlock(plainText));
                        case 2 -> BlockEvent.pageEnd(1);
                        default -> throw new NoSuchElementException("No more Markdown block events");
                    };
                }
            };

            Stream<BlockEvent> stream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false
            );
            return Optional.of(stream);
        } catch (IOException e) {
            throw new ParseException("Failed to stream parse Markdown document", e);
        }
    }
}
