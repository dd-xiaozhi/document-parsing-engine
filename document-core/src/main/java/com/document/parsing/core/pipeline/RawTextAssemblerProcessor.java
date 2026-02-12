package com.document.parsing.core.pipeline;

import com.document.parsing.core.model.Block;
import com.document.parsing.core.model.BlockType;
import com.document.parsing.core.model.Document;
import com.document.parsing.core.model.Page;
import com.document.parsing.core.model.TextBlock;

public class RawTextAssemblerProcessor implements DocumentProcessor {

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public Document process(Document document, ProcessingContext context) {
        if (document.getRawText() != null && !document.getRawText().isBlank()) {
            return document;
        }

        StringBuilder sb = new StringBuilder();
        for (Page page : document.getPages()) {
            for (Block block : page.getBlocks()) {
                if (block.getType() == BlockType.TEXT) {
                    String text = ((TextBlock) block).getText();
                    if (!text.isBlank()) {
                        sb.append(text).append(System.lineSeparator());
                    }
                }
            }
        }

        document.setRawText(sb.toString().trim());
        return document;
    }
}
