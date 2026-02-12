package com.document.parsing.excel;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.CorruptedDocumentException;
import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.Metadata;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.Table;
import com.document.parsing.core.model.TableBlock;
import com.document.parsing.core.model.TextBlock;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;
import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
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

public class ExcelDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.XLSX;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try (OPCPackage pkg = OPCPackage.open(request.getStream())) {
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            DataFormatter formatter = new DataFormatter();

            List<Page> pages = new ArrayList<>();
            List<Table> tables = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();
            Metadata metadata = new Metadata();

            int maxSheets = request.getOptions().getMaxPages();
            int sheetCount = 0;

            XSSFReader.SheetIterator iterator = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (iterator.hasNext()) {
                if (maxSheets > 0 && sheetCount >= maxSheets) {
                    break;
                }

                sheetCount++;
                try (InputStream sheetInput = iterator.next()) {
                    String sheetName = iterator.getSheetName();
                    SheetCaptureHandler handler = parseSheet(sheetInput, styles, sharedStrings, formatter);

                    Table table = new Table("excel-sheet-" + sheetCount, sheetCount, handler.rows());
                    tables.add(table);

                    List<Block> blocks = new ArrayList<>();
                    blocks.add(new TableBlock(table));
                    if (!handler.text().isBlank()) {
                        blocks.add(new TextBlock(handler.text()));
                    }
                    pages.add(new Page(sheetCount, blocks));

                    rawText.append("# ").append(sheetName).append(System.lineSeparator());
                    if (!handler.text().isBlank()) {
                        rawText.append(handler.text()).append(System.lineSeparator());
                    }
                }
            }

            metadata.setSheetCount(sheetCount);
            metadata.setPageCount(sheetCount);

            Document document = Document.builder()
                .metadata(metadata)
                .pages(pages)
                .tables(tables)
                .rawText(rawText.toString().trim())
                .build();
            return ParseResult.of(document);
        } catch (Exception e) {
            throw new CorruptedDocumentException("Failed to parse XLSX document", e);
        }
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        try {
            OPCPackage pkg = OPCPackage.open(request.getStream());
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            DataFormatter formatter = new DataFormatter();
            XSSFReader.SheetIterator sheetIterator = (XSSFReader.SheetIterator) reader.getSheetsData();

            ExcelBlockEventIterator iterator = new ExcelBlockEventIterator(
                pkg,
                sheetIterator,
                styles,
                sharedStrings,
                formatter,
                request.getOptions().getMaxPages()
            );

            Stream<BlockEvent> stream = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                    false
                )
                .onClose(iterator::close);
            return Optional.of(stream);
        } catch (Exception e) {
            throw new CorruptedDocumentException("Failed to stream parse XLSX document", e);
        }
    }

    @Override
    public int getPriority() {
        return 30;
    }

    private SheetCaptureHandler parseSheet(InputStream sheetInput,
                                           StylesTable styles,
                                           ReadOnlySharedStringsTable sharedStrings,
                                           DataFormatter formatter) throws Exception {
        SheetCaptureHandler handler = new SheetCaptureHandler();
        XMLReader parser = SAXHelper.newXMLReader();
        parser.setContentHandler(new XSSFSheetXMLHandler(
            styles,
            null,
            sharedStrings,
            handler,
            formatter,
            false
        ));
        parser.parse(new InputSource(sheetInput));
        return handler;
    }

    private static final class SheetCaptureHandler implements XSSFSheetXMLHandler.SheetContentsHandler {
        private final List<List<String>> rows = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();

        private List<String> currentRow = new ArrayList<>();
        private int currentCol = -1;

        @Override
        public void startRow(int rowNum) {
            currentRow = new ArrayList<>();
            currentCol = -1;
        }

        @Override
        public void endRow(int rowNum) {
            rows.add(currentRow);
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int thisCol = currentCol + 1;
            if (cellReference != null) {
                thisCol = new CellReference(cellReference).getCol();
            }

            while (currentCol + 1 < thisCol) {
                currentRow.add("");
                currentCol++;
            }

            String value = formattedValue == null ? "" : formattedValue;
            currentRow.add(value);
            currentCol = thisCol;

            if (!value.isBlank()) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(value);
            }
        }

        @Override
        public void headerFooter(String text, boolean isHeader, String tagName) {
            // Header/footer content is ignored in MVP.
        }

        public List<List<String>> rows() {
            return rows;
        }

        public String text() {
            return text.toString().trim();
        }
    }

    private final class ExcelBlockEventIterator implements Iterator<BlockEvent>, AutoCloseable {
        private final OPCPackage pkg;
        private final XSSFReader.SheetIterator sheetIterator;
        private final StylesTable styles;
        private final ReadOnlySharedStringsTable sharedStrings;
        private final DataFormatter formatter;
        private final int maxSheets;
        private final Deque<BlockEvent> queue = new ArrayDeque<>();

        private int sheetIndex;
        private boolean closed;

        private ExcelBlockEventIterator(OPCPackage pkg,
                                        XSSFReader.SheetIterator sheetIterator,
                                        StylesTable styles,
                                        ReadOnlySharedStringsTable sharedStrings,
                                        DataFormatter formatter,
                                        int maxSheets) {
            this.pkg = pkg;
            this.sheetIterator = sheetIterator;
            this.styles = styles;
            this.sharedStrings = sharedStrings;
            this.formatter = formatter;
            this.maxSheets = maxSheets;
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
                throw new NoSuchElementException("No more XLSX block events");
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
                pkg.close();
            } catch (Exception ignored) {
                // Ignore close failure.
            }
        }

        private void fillQueue() {
            if (closed) {
                return;
            }

            while (queue.isEmpty() && sheetIterator.hasNext()) {
                if (maxSheets > 0 && sheetIndex >= maxSheets) {
                    close();
                    return;
                }

                sheetIndex++;
                try (InputStream sheetInput = sheetIterator.next()) {
                    String sheetName = sheetIterator.getSheetName();
                    SheetCaptureHandler handler = parseSheet(sheetInput, styles, sharedStrings, formatter);
                    Table table = new Table("excel-sheet-" + sheetIndex, sheetIndex, handler.rows());

                    queue.addLast(BlockEvent.pageStart(sheetIndex));
                    queue.addLast(BlockEvent.block(sheetIndex, new TableBlock(table)));
                    if (!handler.text().isBlank()) {
                        String sheetText = "# " + sheetName + System.lineSeparator() + handler.text();
                        queue.addLast(BlockEvent.block(sheetIndex, new TextBlock(sheetText)));
                    }
                    queue.addLast(BlockEvent.pageEnd(sheetIndex));
                } catch (Exception ex) {
                    close();
                    throw new CorruptedDocumentException("Failed to stream parse XLSX sheet " + sheetIndex, ex);
                }
            }

            if (queue.isEmpty()) {
                close();
            }
        }
    }
}
