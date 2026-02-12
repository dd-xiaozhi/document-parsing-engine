package com.document.parsing.pdf;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.CorruptedDocumentException;
import com.document.parsing.core.exception.OcrUnavailableException;
import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.ImageBlock;
import com.document.parsing.core.model.ImageElement;
import com.document.parsing.core.model.Metadata;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.ParseWarning;
import com.document.parsing.core.model.Table;
import com.document.parsing.core.model.TableBlock;
import com.document.parsing.core.model.TextBlock;
import com.document.parsing.core.ocr.OcrService;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class PdfDocumentParser implements DocumentParser {
    private final List<OcrService> ocrServices;

    public PdfDocumentParser() {
        this.ocrServices = ServiceLoader.load(OcrService.class).stream()
            .map(ServiceLoader.Provider::get)
            .sorted(Comparator.comparingInt(OcrService::getPriority))
            .toList();
    }

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.PDF;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        List<ParseWarning> warnings = new ArrayList<>();
        try (PDDocument pdDocument = PDDocument.load(request.getStream())) {
            int totalPages = pdDocument.getNumberOfPages();
            int pageLimit = resolvePageLimit(totalPages, request.getOptions());
            PDFTextStripper stripper = new PDFTextStripper();

            Metadata metadata = readMetadata(pdDocument, pageLimit);
            List<Page> pages = new ArrayList<>();
            List<ImageElement> images = new ArrayList<>();
            List<Table> tables = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();
            int tableCounter = 0;

            for (int pageNum = 1; pageNum <= pageLimit; pageNum++) {
                String text = extractPageText(stripper, pdDocument, pageNum);

                List<Block> blocks = new ArrayList<>();
                if (!text.isBlank()) {
                    blocks.add(new TextBlock(text));
                    rawText.append(text).append(System.lineSeparator());
                }

                List<Table> pageTables = extractTablesFromText(text, pageNum, tableCounter);
                tableCounter += pageTables.size();
                for (Table table : pageTables) {
                    tables.add(table);
                    blocks.add(new TableBlock(table));
                }

                PDPage page = pdDocument.getPage(pageNum - 1);
                extractImages(page, pageNum, images, blocks, warnings);
                pages.add(new Page(pageNum, blocks));
            }

            maybeRunOcr(rawText, images, request.getOptions(), warnings);

            Document document = Document.builder()
                .metadata(metadata)
                .pages(pages)
                .tables(tables)
                .images(images)
                .rawText(rawText.toString().trim())
                .warnings(warnings)
                .build();
            return new ParseResult(document, warnings);
        } catch (IOException e) {
            throw new CorruptedDocumentException("Failed to parse PDF document", e);
        }
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        try {
            PDDocument pdDocument = PDDocument.load(request.getStream());
            int pageLimit = resolvePageLimit(pdDocument.getNumberOfPages(), request.getOptions());
            PDFTextStripper stripper = new PDFTextStripper();

            PdfBlockEventIterator iterator = new PdfBlockEventIterator(pdDocument, stripper, pageLimit, request.getOptions());
            Stream<BlockEvent> stream = StreamSupport.stream(
                    Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED | Spliterator.NONNULL),
                    false
                )
                .onClose(iterator::close);
            return Optional.of(stream);
        } catch (IOException e) {
            throw new CorruptedDocumentException("Failed to parse PDF stream", e);
        }
    }

    @Override
    public int getPriority() {
        return 20;
    }

    private Metadata readMetadata(PDDocument pdDocument, int pageCount) {
        Metadata metadata = new Metadata();
        metadata.setPageCount(pageCount);

        PDDocumentInformation info = pdDocument.getDocumentInformation();
        if (info != null) {
            metadata.setTitle(info.getTitle());
            metadata.setAuthor(info.getAuthor());
            metadata.setCreator(info.getCreator());
            if (info.getCreationDate() != null) {
                metadata.setCreatedAt(Instant.ofEpochMilli(info.getCreationDate().getTimeInMillis()));
            }
            if (info.getModificationDate() != null) {
                metadata.setModifiedAt(Instant.ofEpochMilli(info.getModificationDate().getTimeInMillis()));
            }
        }
        metadata.getCustomProperties().put("parsedAt", Instant.now().atOffset(ZoneOffset.UTC).toString());
        return metadata;
    }

    private String extractPageText(PDFTextStripper stripper, PDDocument pdDocument, int pageNum) throws IOException {
        stripper.setStartPage(pageNum);
        stripper.setEndPage(pageNum);
        return Optional.ofNullable(stripper.getText(pdDocument)).orElse("").trim();
    }

    private void extractImages(PDPage page,
                               int pageNum,
                               List<ImageElement> images,
                               List<Block> blocks,
                               List<ParseWarning> warnings) {
        try {
            PDResources resources = page.getResources();
            if (resources == null) {
                return;
            }

            int imageCounter = 0;
            for (COSName xObjectName : resources.getXObjectNames()) {
                PDXObject xObject = resources.getXObject(xObjectName);
                if (xObject instanceof PDImageXObject imageObject) {
                    imageCounter++;
                    byte[] content = toPngBytes(imageObject);
                    ImageElement image = new ImageElement(
                        "pdf-page-" + pageNum + "-img-" + imageCounter,
                        pageNum,
                        "image/png",
                        imageObject.getWidth(),
                        imageObject.getHeight(),
                        content
                    );
                    images.add(image);
                    blocks.add(new ImageBlock(image));
                }
            }
        } catch (IOException e) {
            warnings.add(new ParseWarning("PDF_IMAGE_EXTRACTION_FAILED",
                "Failed to extract images from page " + pageNum + ": " + e.getMessage()));
        }
    }

    private byte[] toPngBytes(PDImageXObject imageObject) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(imageObject.getImage(), "png", out);
            return out.toByteArray();
        }
    }

    private List<Table> extractTablesFromText(String pageText, int pageNum, int startIndex) {
        if (pageText == null || pageText.isBlank()) {
            return List.of();
        }

        String[] lines = pageText.split("\\R");
        List<Table> tables = new ArrayList<>();
        List<List<String>> currentRows = new ArrayList<>();
        int localCounter = 0;

        for (String line : lines) {
            Optional<List<String>> maybeRow = parsePotentialTableRow(line);
            if (maybeRow.isPresent()) {
                currentRows.add(maybeRow.get());
            } else {
                if (currentRows.size() >= 2) {
                    localCounter++;
                    tables.add(new Table(
                        "pdf-table-" + pageNum + "-" + (startIndex + localCounter),
                        pageNum,
                        new ArrayList<>(currentRows)
                    ));
                }
                currentRows.clear();
            }
        }

        if (currentRows.size() >= 2) {
            localCounter++;
            tables.add(new Table(
                "pdf-table-" + pageNum + "-" + (startIndex + localCounter),
                pageNum,
                new ArrayList<>(currentRows)
            ));
        }

        return tables;
    }

    private Optional<List<String>> parsePotentialTableRow(String line) {
        if (line == null) {
            return Optional.empty();
        }

        String normalized = line.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        String[] rawCells;
        if (normalized.contains("|") && normalized.chars().filter(ch -> ch == '|').count() >= 2) {
            rawCells = normalized.split("\\|");
        } else if (normalized.contains("\t")) {
            rawCells = normalized.split("\\t");
        } else if (normalized.matches(".*\\S\\s{2,}\\S.*")) {
            rawCells = normalized.split("\\s{2,}");
        } else {
            return Optional.empty();
        }

        List<String> cells = Arrays.stream(rawCells)
            .map(String::trim)
            .filter(cell -> !cell.isBlank())
            .toList();

        if (cells.size() < 2 || isSeparatorRow(cells)) {
            return Optional.empty();
        }

        return Optional.of(cells);
    }

    private boolean isSeparatorRow(List<String> cells) {
        return cells.stream().allMatch(cell -> cell.matches("[-:]+"));
    }

    private void maybeRunOcr(StringBuilder rawText,
                             List<ImageElement> images,
                             ParseOptions options,
                             List<ParseWarning> warnings) {
        if (!options.isEnableOcr() || images.isEmpty()) {
            return;
        }

        int textLength = rawText.toString().trim().length();
        int densityThreshold = options.getLowTextDensityThreshold();
        if (textLength >= densityThreshold) {
            return;
        }

        OcrService ocrService = selectAvailableOcrService();
        if (ocrService == null) {
            String message = "OCR enabled but no OcrService implementation is available";
            if (options.isFailOnOcrError()) {
                throw new OcrUnavailableException(message, null);
            }
            warnings.add(new ParseWarning("OCR_SERVICE_UNAVAILABLE", message));
            return;
        }

        StringBuilder ocrTextBuilder = new StringBuilder();
        for (ImageElement image : images) {
            try {
                String ocrText = ocrService.extractText(image.getContent(), image.getMimeType(), options);
                if (ocrText != null && !ocrText.isBlank()) {
                    ocrTextBuilder.append(ocrText.trim()).append(System.lineSeparator());
                }
            } catch (RuntimeException ex) {
                if (options.isFailOnOcrError()) {
                    throw ex;
                }
                warnings.add(new ParseWarning("OCR_EXECUTION_FAILED", ex.getMessage()));
            }
        }

        if (ocrTextBuilder.length() > 0) {
            if (rawText.length() > 0) {
                rawText.append(System.lineSeparator());
            }
            rawText.append(ocrTextBuilder.toString().trim());
        }
    }

    private String maybeRunOcrForPage(String pageText,
                                      List<ImageElement> images,
                                      ParseOptions options,
                                      List<ParseWarning> warnings) {
        if (!options.isEnableOcr() || images.isEmpty()) {
            return "";
        }

        int textLength = pageText == null ? 0 : pageText.trim().length();
        if (textLength >= options.getLowTextDensityThreshold()) {
            return "";
        }

        OcrService ocrService = selectAvailableOcrService();
        if (ocrService == null) {
            if (options.isFailOnOcrError()) {
                throw new OcrUnavailableException("OCR enabled but no OcrService implementation is available", null);
            }
            warnings.add(new ParseWarning("OCR_SERVICE_UNAVAILABLE", "OCR service is unavailable for streaming"));
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (ImageElement image : images) {
            try {
                String text = ocrService.extractText(image.getContent(), image.getMimeType(), options);
                if (text != null && !text.isBlank()) {
                    builder.append(text.trim()).append(System.lineSeparator());
                }
            } catch (RuntimeException ex) {
                if (options.isFailOnOcrError()) {
                    throw ex;
                }
                warnings.add(new ParseWarning("OCR_EXECUTION_FAILED", ex.getMessage()));
            }
        }
        return builder.toString().trim();
    }

    private OcrService selectAvailableOcrService() {
        return ocrServices.stream().filter(OcrService::isAvailable).findFirst().orElse(null);
    }

    private int resolvePageLimit(int totalPages, ParseOptions options) {
        int configured = options.getMaxPages();
        if (configured <= 0) {
            return totalPages;
        }
        return Math.min(totalPages, configured);
    }

    private final class PdfBlockEventIterator implements Iterator<BlockEvent>, AutoCloseable {
        private final PDDocument document;
        private final PDFTextStripper stripper;
        private final int pageLimit;
        private final ParseOptions options;
        private final Deque<BlockEvent> queue = new ArrayDeque<>();

        private int currentPage = 1;
        private int tableCounter = 0;
        private boolean closed;

        private PdfBlockEventIterator(PDDocument document, PDFTextStripper stripper, int pageLimit, ParseOptions options) {
            this.document = document;
            this.stripper = stripper;
            this.pageLimit = pageLimit;
            this.options = options;
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
                throw new NoSuchElementException("No more PDF block events");
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
                // Ignore close errors for iterator lifecycle.
            }
        }

        private void fillQueue() {
            if (closed) {
                return;
            }

            while (queue.isEmpty() && currentPage <= pageLimit) {
                int pageNum = currentPage++;
                try {
                    String pageText = extractPageText(stripper, document, pageNum);
                    queue.addLast(BlockEvent.pageStart(pageNum));

                    if (!pageText.isBlank()) {
                        queue.addLast(BlockEvent.block(pageNum, new TextBlock(pageText)));
                    }

                    List<Table> tables = extractTablesFromText(pageText, pageNum, tableCounter);
                    tableCounter += tables.size();
                    for (Table table : tables) {
                        queue.addLast(BlockEvent.block(pageNum, new TableBlock(table)));
                    }

                    List<ImageElement> images = new ArrayList<>();
                    List<Block> blocks = new ArrayList<>();
                    List<ParseWarning> warnings = new ArrayList<>();
                    extractImages(document.getPage(pageNum - 1), pageNum, images, blocks, warnings);
                    for (Block block : blocks) {
                        queue.addLast(BlockEvent.block(pageNum, block));
                    }

                    String ocrText = maybeRunOcrForPage(pageText, images, options, warnings);
                    if (!ocrText.isBlank()) {
                        queue.addLast(BlockEvent.block(pageNum, new TextBlock(ocrText)));
                    }

                    queue.addLast(BlockEvent.pageEnd(pageNum));
                } catch (RuntimeException | IOException ex) {
                    close();
                    throw new CorruptedDocumentException("Failed to stream parse PDF page " + pageNum, ex);
                }
            }

            if (queue.isEmpty()) {
                close();
            }
        }
    }
}
