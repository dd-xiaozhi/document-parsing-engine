package com.document.parsing.core.model;

import java.util.Objects;

public class TextBlock implements Block {
    private final String text;

    public TextBlock(String text) {
        this.text = Objects.requireNonNullElse(text, "");
    }

    public String getText() {
        return text;
    }

    @Override
    public BlockType getType() {
        return BlockType.TEXT;
    }
}
