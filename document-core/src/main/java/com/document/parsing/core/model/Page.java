package com.document.parsing.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Page {
    private final int pageNumber;
    private final List<Block> blocks;

    public Page(int pageNumber, List<Block> blocks) {
        this.pageNumber = pageNumber;
        this.blocks = new ArrayList<>(Objects.requireNonNullElse(blocks, List.of()));
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public List<Block> getBlocks() {
        return blocks;
    }
}
