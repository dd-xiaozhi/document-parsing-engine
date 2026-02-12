# Spring Boot Starter

Auto-configured bean:

- `DocumentEngine`

Main properties under `document.engine`:

- `async.core-pool-size`
- `async.max-pool-size`
- `async.queue-capacity`
- `ocr.enabled`
- `ocr.fail-on-error`
- `ocr.data-path`
- `parser.max-pages`
- `parser.charset`
- `parser.low-text-density-threshold`
- `pipeline.watermark-cleanup-enabled`

## Inject And Use

```java
@Service
public class DocumentService {
    private final DocumentEngine documentEngine;

    public DocumentService(DocumentEngine documentEngine) {
        this.documentEngine = documentEngine;
    }

    public Document parseSingle(File file) {
        return documentEngine.parse(file);
    }
}
```

## Batch Parsing

Synchronous:

```java
List<Document> documents = documentEngine.parseBatch(List.of(
    new File("/data/a.docx"),
    new File("/data/b.pptx")
));
```

Asynchronous:

```java
CompletableFuture<List<Document>> future = documentEngine.parseBatchAsync(List.of(
    new File("/data/a.pdf"),
    new File("/data/b.xlsx")
));
List<Document> documents = future.join();
```
