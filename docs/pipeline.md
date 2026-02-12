# Pipeline Extension

Implement `DocumentProcessor` and register it through `DocumentEngine.builder().addProcessor(...)`.

Processors run in ascending `getOrder()`.

- `RawTextAssemblerProcessor`: assembles `rawText` from text blocks
- `WatermarkCleanupProcessor`: optional watermark text cleanup
