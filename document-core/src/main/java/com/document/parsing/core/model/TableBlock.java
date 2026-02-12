package com.document.parsing.core.model;

import java.util.Objects;

public class TableBlock implements Block {
    private final Table table;

    public TableBlock(Table table) {
        this.table = Objects.requireNonNull(table, "table must not be null");
    }

    public Table getTable() {
        return table;
    }

    @Override
    public BlockType getType() {
        return BlockType.TABLE;
    }
}
