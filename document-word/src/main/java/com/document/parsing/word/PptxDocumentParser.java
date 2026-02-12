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
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFPictureShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTableCell;
import org.apache.poi.xslf.usermodel.XSLFTableRow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class PptxDocumentParser implements DocumentParser {

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.PPTX;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try (XMLSlideShow slideShow = new XMLSlideShow(request.getStream())) {
            Metadata metadata = readMetadata(slideShow);
            List<Page> pages = new ArrayList<>();
            List<Table> tables = new ArrayList<>();
            List<ImageElement> images = new ArrayList<>();
            StringBuilder rawText = new StringBuilder();

            List<XSLFSlide> slides = slideShow.getSlides();
            int slideLimit = resolveSlideLimit(slides.size(), request.getOptions());
            int tableIndex = 0;
            int imageIndex = 0;

            for (int i = 0; i < slideLimit; i++) {
                int pageNumber = i + 1;
                XSLFSlide slide = slides.get(i);
                List<Block> blocks = new ArrayList<>();

                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = normalizeText(textShape.getText());
                        if (!text.isBlank()) {
                            blocks.add(new TextBlock(text));
                            rawText.append(text).append(System.lineSeparator());
                        }
                    } else if (shape instanceof XSLFTable tableShape) {
                        tableIndex++;
                        Table table = toTable(tableShape, pageNumber, tableIndex);
                        tables.add(table);
                        blocks.add(new TableBlock(table));
                        appendTableText(rawText, table);
                    } else if (shape instanceof XSLFPictureShape pictureShape) {
                        imageIndex++;
                        ImageElement image = toImageElement(pictureShape, pageNumber, imageIndex);
                        if (image != null) {
                            images.add(image);
                            blocks.add(new ImageBlock(image));
                        }
                    }
                }

                pages.add(new Page(pageNumber, blocks));
            }

            metadata.setPageCount(slideLimit);
            metadata.getCustomProperties().put("totalSlides", slides.size());

            Document document = Document.builder()
                .metadata(metadata)
                .pages(pages)
                .tables(tables)
                .images(images)
                .rawText(rawText.toString().trim())
                .build();
            return ParseResult.of(document);
        } catch (IOException e) {
            throw new CorruptedDocumentException("Failed to parse PPTX document", e);
        }
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        Document document = parse(request).getDocument();
        return Optional.of(toBlockEvents(document));
    }

    @Override
    public int getPriority() {
        return 30;
    }

    private Metadata readMetadata(XMLSlideShow slideShow) {
        Metadata metadata = new Metadata();
        POIXMLProperties properties = slideShow.getProperties();
        if (properties == null) {
            return metadata;
        }

        POIXMLProperties.CoreProperties core = properties.getCoreProperties();
        if (core != null) {
            metadata.setTitle(core.getTitle());
            metadata.setAuthor(core.getCreator());
            metadata.setCreator(core.getLastModifiedByUser());
        }
        return metadata;
    }

    private int resolveSlideLimit(int totalSlides, ParseOptions options) {
        if (options.getMaxPages() <= 0) {
            return totalSlides;
        }
        return Math.min(totalSlides, options.getMaxPages());
    }

    private Table toTable(XSLFTable tableShape, int pageNumber, int tableIndex) {
        List<List<String>> rows = new ArrayList<>();
        for (XSLFTableRow row : tableShape.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XSLFTableCell cell : row.getCells()) {
                cells.add(normalizeText(cell.getText()));
            }
            rows.add(cells);
        }
        return new Table("pptx-table-" + tableIndex, pageNumber, rows);
    }

    private ImageElement toImageElement(XSLFPictureShape pictureShape, int pageNumber, int imageIndex) {
        XSLFPictureData pictureData = pictureShape.getPictureData();
        if (pictureData == null) {
            return null;
        }
        String mimeType = Optional.ofNullable(pictureData.getContentType()).orElse("image/*");
        return new ImageElement("pptx-image-" + imageIndex, pageNumber, mimeType, -1, -1, pictureData.getData());
    }

    private void appendTableText(StringBuilder rawText, Table table) {
        for (List<String> row : table.getRows()) {
            String line = String.join("\t", row);
            if (!line.isBlank()) {
                rawText.append(line).append(System.lineSeparator());
            }
        }
    }

    private Stream<BlockEvent> toBlockEvents(Document document) {
        List<BlockEvent> events = new ArrayList<>();
        for (Page page : document.getPages()) {
            events.add(BlockEvent.pageStart(page.getPageNumber()));
            for (Block block : page.getBlocks()) {
                events.add(BlockEvent.block(page.getPageNumber(), block));
            }
            events.add(BlockEvent.pageEnd(page.getPageNumber()));
        }
        return events.stream();
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }
}
