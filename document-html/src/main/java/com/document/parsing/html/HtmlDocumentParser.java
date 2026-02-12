package com.document.parsing.html;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.ParseException;
import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.ImageBlock;
import com.document.parsing.core.model.ImageElement;
import com.document.parsing.core.model.Metadata;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.Table;
import com.document.parsing.core.model.TableBlock;
import com.document.parsing.core.model.TextBlock;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class HtmlDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.HTML;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try {
            org.jsoup.nodes.Document html = Jsoup.parse(request.getStream(), request.getOptions().getCharset().name(), "");
            Metadata metadata = new Metadata();
            metadata.setTitle(html.title());
            metadata.setPageCount(1);

            List<Block> blocks = new ArrayList<>();
            List<Table> tables = new ArrayList<>();
            List<ImageElement> images = new ArrayList<>();

            String bodyText = html.body() == null ? "" : html.body().text();
            if (!bodyText.isBlank()) {
                blocks.add(new TextBlock(bodyText));
            }

            Elements tableElements = html.select("table");
            int tableIndex = 0;
            for (Element tableElement : tableElements) {
                tableIndex++;
                List<List<String>> rows = new ArrayList<>();
                for (Element tr : tableElement.select("tr")) {
                    List<String> row = new ArrayList<>();
                    for (Element td : tr.select("th,td")) {
                        row.add(td.text());
                    }
                    rows.add(row);
                }
                Table table = new Table("html-table-" + tableIndex, 1, rows);
                tables.add(table);
                blocks.add(new TableBlock(table));
            }

            Elements imageElements = html.select("img");
            int imageIndex = 0;
            for (Element imageElement : imageElements) {
                imageIndex++;
                int width = parseDimension(imageElement.attr("width"));
                int height = parseDimension(imageElement.attr("height"));
                ImageElement image = new ImageElement(
                    "html-image-" + imageIndex,
                    1,
                    "image/*",
                    width,
                    height,
                    null
                );
                images.add(image);
                blocks.add(new ImageBlock(image));
            }

            Document document = Document.builder()
                .metadata(metadata)
                .pages(List.of(new Page(1, blocks)))
                .tables(tables)
                .images(images)
                .rawText(bodyText)
                .build();
            return ParseResult.of(document);
        } catch (IOException e) {
            throw new ParseException("Failed to parse HTML document", e);
        }
    }

    @Override
    public int getPriority() {
        return 40;
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        try {
            org.jsoup.nodes.Document html = Jsoup.parse(request.getStream(), request.getOptions().getCharset().name(), "");
            HtmlBlockEventIterator iterator = new HtmlBlockEventIterator(html);
            Stream<BlockEvent> stream = StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                false
            );
            return Optional.of(stream);
        } catch (IOException e) {
            throw new ParseException("Failed to stream parse HTML document", e);
        }
    }

    private final class HtmlBlockEventIterator implements Iterator<BlockEvent> {
        private final String bodyText;
        private final Iterator<Element> tableIterator;
        private final Iterator<Element> imageIterator;
        private final Deque<BlockEvent> queue = new ArrayDeque<>();

        private int stage;
        private int tableIndex;
        private int imageIndex;

        private HtmlBlockEventIterator(org.jsoup.nodes.Document html) {
            this.bodyText = html.body() == null ? "" : html.body().text();
            this.tableIterator = html.select("table").iterator();
            this.imageIterator = html.select("img").iterator();
        }

        @Override
        public boolean hasNext() {
            if (queue.isEmpty()) {
                fillQueue();
            }
            return !queue.isEmpty();
        }

        @Override
        public BlockEvent next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more HTML block events");
            }
            return queue.removeFirst();
        }

        private void fillQueue() {
            while (queue.isEmpty()) {
                switch (stage) {
                    case 0 -> {
                        stage = 1;
                        queue.addLast(BlockEvent.pageStart(1));
                    }
                    case 1 -> {
                        stage = 2;
                        if (!bodyText.isBlank()) {
                            queue.addLast(BlockEvent.block(1, new TextBlock(bodyText)));
                        }
                    }
                    case 2 -> {
                        if (tableIterator.hasNext()) {
                            tableIndex++;
                            Element tableElement = tableIterator.next();
                            List<List<String>> rows = new ArrayList<>();
                            for (Element tr : tableElement.select("tr")) {
                                List<String> row = new ArrayList<>();
                                for (Element td : tr.select("th,td")) {
                                    row.add(td.text());
                                }
                                rows.add(row);
                            }
                            queue.addLast(BlockEvent.block(1, new TableBlock(new Table("html-table-" + tableIndex, 1, rows))));
                        } else {
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (imageIterator.hasNext()) {
                            imageIndex++;
                            Element imageElement = imageIterator.next();
                            ImageElement image = new ImageElement(
                                "html-image-" + imageIndex,
                                1,
                                "image/*",
                                parseDimension(imageElement.attr("width")),
                                parseDimension(imageElement.attr("height")),
                                null
                            );
                            queue.addLast(BlockEvent.block(1, new ImageBlock(image)));
                        } else {
                            stage = 4;
                        }
                    }
                    case 4 -> {
                        stage = 5;
                        queue.addLast(BlockEvent.pageEnd(1));
                    }
                    default -> {
                        return;
                    }
                }
            }
        }
    }

    private int parseDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(dimension.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
