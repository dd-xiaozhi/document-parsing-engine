package com.document.parsing.ocr;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.OcrUnavailableException;
import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.ImageBlock;
import com.document.parsing.core.model.ImageElement;
import com.document.parsing.core.model.Metadata;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.ParseWarning;
import com.document.parsing.core.model.TextBlock;
import com.document.parsing.core.ocr.OcrService;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public class ImageDocumentParser implements DocumentParser {
    private final List<OcrService> ocrServices;

    public ImageDocumentParser() {
        this.ocrServices = ServiceLoader.load(OcrService.class).stream()
            .map(ServiceLoader.Provider::get)
            .sorted(Comparator.comparingInt(OcrService::getPriority))
            .toList();
    }

    @Override
    public boolean supports(DocumentType type) {
        return type == DocumentType.IMAGE;
    }

    @Override
    public ParseResult parse(ParseRequest request) {
        try {
            byte[] data = request.getStream().readAllBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            int width = image == null ? -1 : image.getWidth();
            int height = image == null ? -1 : image.getHeight();

            String mimeType = detectMimeType(request.getFileName());
            ImageElement imageElement = new ImageElement("image-1", 1, mimeType, width, height, data);

            List<Block> blocks = new ArrayList<>();
            blocks.add(new ImageBlock(imageElement));

            List<ParseWarning> warnings = new ArrayList<>();
            String ocrText = "";
            if (request.getOptions().isEnableOcr()) {
                ocrText = runOcr(data, mimeType, request.getOptions(), warnings);
                if (!ocrText.isBlank()) {
                    blocks.add(new TextBlock(ocrText));
                }
            }

            Metadata metadata = new Metadata();
            metadata.setPageCount(1);

            Document document = Document.builder()
                .metadata(metadata)
                .pages(List.of(new Page(1, blocks)))
                .images(List.of(imageElement))
                .rawText(ocrText)
                .warnings(warnings)
                .build();

            return new ParseResult(document, warnings);
        } catch (IOException e) {
            throw new OcrUnavailableException("Failed to parse image input", e);
        }
    }

    @Override
    public int getPriority() {
        return 25;
    }

    @Override
    public Optional<Stream<BlockEvent>> parseStream(ParseRequest request) {
        ParseResult result = parse(request);
        List<BlockEvent> events = new ArrayList<>();
        events.add(BlockEvent.pageStart(1));
        for (Block block : result.getDocument().getPages().get(0).getBlocks()) {
            events.add(BlockEvent.block(1, block));
        }
        events.add(BlockEvent.pageEnd(1));
        return Optional.of(events.stream());
    }

    private String runOcr(byte[] data, String mimeType, ParseOptions options, List<ParseWarning> warnings) {
        OcrService service = ocrServices.stream().filter(OcrService::isAvailable).findFirst().orElse(null);
        if (service == null) {
            if (options.isFailOnOcrError()) {
                throw new OcrUnavailableException("OCR enabled but no available OcrService implementation", null);
            }
            warnings.add(new ParseWarning("OCR_SERVICE_UNAVAILABLE", "OCR service is not available"));
            return "";
        }

        try {
            return service.extractText(data, mimeType, options);
        } catch (RuntimeException ex) {
            if (options.isFailOnOcrError()) {
                throw ex;
            }
            warnings.add(new ParseWarning("OCR_EXECUTION_FAILED", ex.getMessage()));
            return "";
        }
    }

    private String detectMimeType(String fileName) {
        if (fileName == null) {
            return "image/*";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".tif") || lower.endsWith(".tiff")) {
            return "image/tiff";
        }
        return "image/*";
    }
}
