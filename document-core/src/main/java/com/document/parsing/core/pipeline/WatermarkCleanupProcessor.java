package com.document.parsing.core.pipeline;

import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.BlockType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.TextBlock;

import java.util.List;
import java.util.stream.Collectors;

public class WatermarkCleanupProcessor implements DocumentProcessor {
    private final List<String> watermarkPatterns;

    public WatermarkCleanupProcessor() {
        this(List.of("CONFIDENTIAL", "DRAFT", "SAMPLE"));
    }

    public WatermarkCleanupProcessor(List<String> watermarkPatterns) {
        this.watermarkPatterns = watermarkPatterns;
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public Document process(Document document, ProcessingContext context) {
        if (!context.getRequest().getOptions().isWatermarkCleanupEnabled()) {
            return document;
        }

        for (Page page : document.getPages()) {
            List<Block> cleaned = page.getBlocks().stream()
                .map(this::cleanupBlock)
                .collect(Collectors.toList());
            page.getBlocks().clear();
            page.getBlocks().addAll(cleaned);
        }
        return document;
    }

    private Block cleanupBlock(Block block) {
        if (block.getType() != BlockType.TEXT) {
            return block;
        }

        String text = ((TextBlock) block).getText();
        String cleaned = text;
        for (String pattern : watermarkPatterns) {
            cleaned = cleaned.replace(pattern, "");
        }
        return new TextBlock(cleaned.trim());
    }
}
