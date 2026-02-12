package com.document.parsing.core.model;

import java.util.Objects;

public class ImageBlock implements Block {
    private final ImageElement image;

    public ImageBlock(ImageElement image) {
        this.image = Objects.requireNonNull(image, "image must not be null");
    }

    public ImageElement getImage() {
        return image;
    }

    @Override
    public BlockType getType() {
        return BlockType.IMAGE;
    }
}
