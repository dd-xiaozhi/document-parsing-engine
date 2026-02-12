package com.document.parsing.core.engine;

import com.document.parsing.core.event.BlockEvent;
import com.document.parsing.core.exception.ParseException;
import com.document.parsing.core.exception.UnsupportedFormatException;
import com.document.parsing.core.format.FormatDetector;
import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.DocumentType;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.ParseWarning;
import com.document.parsing.core.parser.DocumentParser;
import com.document.parsing.core.parser.ParseOptions;
import com.document.parsing.core.parser.ParseRequest;
import com.document.parsing.core.parser.ParseResult;
import com.document.parsing.core.parser.ParserRegistry;
import com.document.parsing.core.pipeline.DocumentProcessor;
import com.document.parsing.core.pipeline.ProcessingContext;
import com.document.parsing.core.pipeline.RawTextAssemblerProcessor;
import com.document.parsing.core.pipeline.WatermarkCleanupProcessor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class DocumentEngine implements AutoCloseable {
    private final ParserRegistry parserRegistry;
    private final List<DocumentProcessor> processors;
    private final FormatDetector formatDetector;
    private final ExecutorService executor;
    private final ParseOptions defaultOptions;
    private final boolean shutdownExecutor;

    private DocumentEngine(Builder builder) {
        this.parserRegistry = builder.buildRegistry();
        this.processors = builder.buildProcessors();
        this.formatDetector = builder.formatDetector;
        this.executor = builder.executorService == null ? builder.defaultExecutor() : builder.executorService;
        this.shutdownExecutor = builder.shutdownExecutor;
        this.defaultOptions = builder.defaultOptions;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Document parse(File file) {
        return parse(file, defaultOptions);
    }

    public Document parse(File file, ParseOptions options) {
        Objects.requireNonNull(file, "file must not be null");
        ParseOptions effectiveOptions = options == null ? defaultOptions : options;

        try (InputStream in = Files.newInputStream(file.toPath())) {
            ParseRequest request = ParseRequest.builder()
                .stream(in)
                .fileName(file.getName())
                .size(file.length())
                .hintedType(DocumentType.UNKNOWN)
                .options(effectiveOptions)
                .build();
            return parseInternal(request);
        } catch (IOException e) {
            throw new ParseException("Failed to parse file: " + file.getAbsolutePath(), e);
        }
    }

    public Document parse(InputStream inputStream, String fileName) {
        return parse(inputStream, fileName, defaultOptions);
    }

    public Document parse(InputStream inputStream, String fileName, ParseOptions options) {
        ParseOptions effectiveOptions = options == null ? defaultOptions : options;
        ParseRequest request = ParseRequest.builder()
            .stream(inputStream)
            .fileName(fileName)
            .hintedType(DocumentType.UNKNOWN)
            .options(effectiveOptions)
            .build();
        return parseInternal(request);
    }

    public List<Document> parseBatch(List<File> files) {
        return parseBatch(files, defaultOptions);
    }

    public List<Document> parseBatch(List<File> files, ParseOptions options) {
        ParseOptions effectiveOptions = options == null ? defaultOptions : options;
        List<File> normalizedFiles = normalizeBatchFiles(files);

        List<Document> documents = new ArrayList<>(normalizedFiles.size());
        for (File file : normalizedFiles) {
            documents.add(parse(file, effectiveOptions));
        }
        return List.copyOf(documents);
    }

    public CompletableFuture<Document> parseAsync(File file) {
        return parseAsync(file, defaultOptions);
    }

    public CompletableFuture<Document> parseAsync(File file, ParseOptions options) {
        ParseOptions effectiveOptions = options == null ? defaultOptions : options;
        return CompletableFuture.supplyAsync(() -> parse(file, effectiveOptions), executor);
    }

    public CompletableFuture<List<Document>> parseBatchAsync(List<File> files) {
        return parseBatchAsync(files, defaultOptions);
    }

    public CompletableFuture<List<Document>> parseBatchAsync(List<File> files, ParseOptions options) {
        ParseOptions effectiveOptions = options == null ? defaultOptions : options;
        List<File> normalizedFiles = normalizeBatchFiles(files);

        List<CompletableFuture<Document>> futures = normalizedFiles.stream()
            .map(file -> parseAsync(file, effectiveOptions))
            .toList();

        CompletableFuture<Void> combined = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return combined.thenApply(ignored -> futures.stream()
            .map(CompletableFuture::join)
            .toList());
    }

    public Stream<BlockEvent> parseStream(InputStream inputStream, String fileName) {
        return parseStream(inputStream, fileName, defaultOptions);
    }

    public Stream<BlockEvent> parseStream(InputStream inputStream, String fileName, ParseOptions options) {
        ParseOptions effectiveOptions = options == null ? defaultOptions : options;
        ParseRequest request = ParseRequest.builder()
            .stream(inputStream)
            .fileName(fileName)
            .hintedType(DocumentType.UNKNOWN)
            .options(effectiveOptions)
            .build();

        DocumentType type = formatDetector.detect(request);
        DocumentParser parser = parserRegistry.findParser(type)
            .orElseThrow(() -> new UnsupportedFormatException("No parser available for document type: " + type));

        Stream<BlockEvent> eventStream = parser.parseStream(request)
            .orElseGet(() -> adaptDocumentToEvents(parseInternal(request)));
        return Stream.concat(eventStream, Stream.of(BlockEvent.done()));
    }

    public List<DocumentParser> getRegisteredParsers() {
        return parserRegistry.getParsers();
    }

    private List<File> normalizeBatchFiles(List<File> files) {
        Objects.requireNonNull(files, "files must not be null");
        List<File> normalized = new ArrayList<>(files.size());
        for (File file : files) {
            normalized.add(Objects.requireNonNull(file, "files must not contain null item"));
        }
        return List.copyOf(normalized);
    }

    private Document parseInternal(ParseRequest request) {
        DocumentType type = formatDetector.detect(request);
        DocumentParser parser = parserRegistry.findParser(type)
            .orElseThrow(() -> new UnsupportedFormatException("No parser available for document type: " + type));

        ParseResult parseResult = parser.parse(request);
        Document document = parseResult.getDocument();
        List<ParseWarning> warnings = new ArrayList<>(parseResult.getWarnings());
        if (document.getWarnings() != null) {
            warnings.addAll(document.getWarnings());
        }
        document.setWarnings(warnings);

        ProcessingContext context = new ProcessingContext(request, type);
        for (DocumentProcessor processor : processors) {
            document = processor.process(document, context);
        }
        return document;
    }

    private Stream<BlockEvent> adaptDocumentToEvents(Document document) {
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

    @Override
    public void close() {
        if (shutdownExecutor) {
            executor.shutdown();
        }
    }

    public static final class Builder {
        private final List<DocumentParser> parserList = new ArrayList<>();
        private final List<DocumentProcessor> processorList = new ArrayList<>();
        private boolean autoRegister;
        private FormatDetector formatDetector = new FormatDetector();
        private ExecutorService executorService;
        private boolean shutdownExecutor = true;
        private ParseOptions defaultOptions = ParseOptions.defaultOptions();
        private int asyncCorePoolSize = 4;
        private int asyncMaxPoolSize = 16;
        private int asyncQueueCapacity = 200;

        public Builder autoRegister() {
            this.autoRegister = true;
            return this;
        }

        public Builder register(DocumentParser parser) {
            this.parserList.add(parser);
            return this;
        }

        public Builder registerAll(List<DocumentParser> parsers) {
            this.parserList.addAll(parsers);
            return this;
        }

        public Builder addProcessor(DocumentProcessor processor) {
            this.processorList.add(processor);
            return this;
        }

        public Builder formatDetector(FormatDetector formatDetector) {
            this.formatDetector = formatDetector;
            return this;
        }

        public Builder executorService(ExecutorService executorService, boolean shutdownExecutor) {
            this.executorService = executorService;
            this.shutdownExecutor = shutdownExecutor;
            return this;
        }

        public Builder asyncPool(int corePoolSize, int maxPoolSize, int queueCapacity) {
            this.asyncCorePoolSize = corePoolSize;
            this.asyncMaxPoolSize = maxPoolSize;
            this.asyncQueueCapacity = queueCapacity;
            return this;
        }

        public Builder defaultOptions(ParseOptions defaultOptions) {
            this.defaultOptions = defaultOptions;
            return this;
        }

        public DocumentEngine build() {
            return new DocumentEngine(this);
        }

        private ParserRegistry buildRegistry() {
            ParserRegistry registry = new ParserRegistry();
            for (DocumentParser parser : parserList) {
                registry.register(parser);
            }

            if (autoRegister) {
                ServiceLoader<DocumentParser> serviceLoader = ServiceLoader.load(DocumentParser.class);
                for (DocumentParser parser : serviceLoader) {
                    boolean exists = registry.getParsers().stream()
                        .anyMatch(p -> p.getClass().getName().equals(parser.getClass().getName()));
                    if (!exists) {
                        registry.register(parser);
                    }
                }
            }
            return registry;
        }

        private List<DocumentProcessor> buildProcessors() {
            List<DocumentProcessor> result = new ArrayList<>(processorList);
            if (result.stream().noneMatch(p -> p instanceof RawTextAssemblerProcessor)) {
                result.add(new RawTextAssemblerProcessor());
            }
            if (result.stream().noneMatch(p -> p instanceof WatermarkCleanupProcessor)) {
                result.add(new WatermarkCleanupProcessor());
            }
            result.sort(Comparator.comparingInt(DocumentProcessor::getOrder));
            return List.copyOf(result);
        }

        private ExecutorService defaultExecutor() {
            return new ThreadPoolExecutor(
                asyncCorePoolSize,
                asyncMaxPoolSize,
                60,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(asyncQueueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
            );
        }
    }
}
