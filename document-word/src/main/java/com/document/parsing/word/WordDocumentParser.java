package com.document.parsing.word;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.CorruptedDocumentException;
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
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

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

public class WordDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.DOCX;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try (XWPFDocument word = new XWPFDocument(request.getStream())) {
            ParseOptions options = request.getOptions();
            Metadata metadata = readMetadata(word);
            List<Block> blocks = new ArrayList<>();
            List<Table> tables = new ArrayList<>();
            List<ImageElement> images = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();

            int maxBlocks = options.getMaxPages() > 0 ? options.getMaxPages() * 200 : Integer.MAX_VALUE;
            int blockCount = 0;

            for (IBodyElement element : word.getBodyElements()) {
                if (blockCount >= maxBlocks) {
                    break;
                }

                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        blocks.add(new TextBlock(text));
                        rawText.append(text).append(System.lineSeparator());
                        blockCount++;
                    }
                } else if (element instanceof XWPFTable table) {
                    Table parsedTable = toTable(table, tables.size() + 1);
                    tables.add(parsedTable);
                    blocks.add(new TableBlock(parsedTable));
                    rawText.append(table.getText()).append(System.lineSeparator());
                    blockCount++;
                }
            }

            int imageIndex = 0;
            for (var picData : word.getAllPictures()) {
                imageIndex++;
                ImageElement image = new ImageElement(
                    "word-image-" + imageIndex,
                    1,
                    resolveMimeType(picData),
                    -1,
                    -1,
                    picData.getData()
                );
                images.add(image);
                blocks.add(new ImageBlock(image));
            }

            Page page = new Page(1, blocks);
            metadata.setPageCount(1);

            Document document = Document.builder()
                .metadata(metadata)
                .pages(List.of(page))
                .tables(tables)
                .images(images)
                .rawText(rawText.toString().trim())
                .build();
            return ParseResult.of(document);
        } catch (IOException e) {
            throw new CorruptedDocumentException("Failed to parse DOCX document", e);
        }
    }

    @Override
    public int getPriority() {
        return 30;
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        try {
            XWPFDocument word = new XWPFDocument(request.getStream());
            WordBlockEventIterator iterator = new WordBlockEventIterator(word, request.getOptions());
            Stream<BlockEvent> stream = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                    false
                )
                .onClose(iterator::close);
            return Optional.of(stream);
        } catch (IOException e) {
            throw new CorruptedDocumentException("Failed to stream parse DOCX document", e);
        }
    }

    private final class WordBlockEventIterator implements Iterator<BlockEvent>, AutoCloseable {
        private final XWPFDocument document;
        private final Iterator<IBodyElement> bodyIterator;
        private final Iterator<XWPFPictureData> pictureIterator;
        private final int maxBlocks;
        private final Deque<BlockEvent> queue = new ArrayDeque<>();

        private int blockCount;
        private int tableIndex;
        private int imageIndex;
        private boolean pageStarted;
        private boolean pageEnded;
        private boolean closed;

        private WordBlockEventIterator(XWPFDocument document, ParseOptions options) {
            this.document = document;
            this.bodyIterator = document.getBodyElements().iterator();
            this.pictureIterator = document.getAllPictures().iterator();
            this.maxBlocks = options.getMaxPages() > 0 ? options.getMaxPages() * 200 : Integer.MAX_VALUE;
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
                throw new NoSuchElementException("No more DOCX block events");
            }
            return queue.removeFirst();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                document.close();
            } catch (IOException ignored) {
                // Ignore close exceptions for stream lifecycle.
            }
        }

        private void fillQueue() {
            if (closed) {
                return;
            }

            if (!pageStarted) {
                pageStarted = true;
                queue.addLast(BlockEvent.pageStart(1));
                return;
            }

            while (queue.isEmpty() && blockCount < maxBlocks && bodyIterator.hasNext()) {
                IBodyElement element = bodyIterator.next();
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        blockCount++;
                        queue.addLast(BlockEvent.block(1, new TextBlock(text)));
                    }
                } else if (element instanceof XWPFTable table) {
                    blockCount++;
                    tableIndex++;
                    queue.addLast(BlockEvent.block(1, new TableBlock(toTable(table, tableIndex))));
                }
            }

            while (queue.isEmpty() && pictureIterator.hasNext()) {
                XWPFPictureData picData = pictureIterator.next();
                imageIndex++;
                queue.addLast(BlockEvent.block(1, new ImageBlock(new ImageElement(
                    "word-image-" + imageIndex,
                    1,
                    resolveMimeType(picData),
                    -1,
                    -1,
                    picData.getData()
                ))));
            }

            if (queue.isEmpty() && !pageEnded) {
                pageEnded = true;
                queue.addLast(BlockEvent.pageEnd(1));
                return;
            }

            if (queue.isEmpty()) {
                close();
            }
        }
    }

    private Metadata readMetadata(XWPFDocument document) {
        Metadata metadata = new Metadata();
        POIXMLProperties.CoreProperties core = document.getProperties().getCoreProperties();
        metadata.setTitle(core.getTitle());
        metadata.setAuthor(core.getCreator());
        metadata.setCreator(core.getLastModifiedByUser());
        return metadata;
    }

    private Table toTable(XWPFTable table, int index) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText());
            }
            rows.add(cells);
        }
        return new Table("word-table-" + index, 1, rows);
    }

    private String resolveMimeType(XWPFPictureData picData) {
        if (picData.getPackagePart() != null && picData.getPackagePart().getContentType() != null) {
            return picData.getPackagePart().getContentType();
        }
        return "image/*";
    }
}
