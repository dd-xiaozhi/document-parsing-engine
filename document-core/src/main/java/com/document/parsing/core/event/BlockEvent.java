package com.document.parsing.core.event;

import com.document.parsing.core.model.Block;

public class BlockEvent {
    private final BlockEventType type;
    private final int pageNumber;
    private final Block block;

    private BlockEvent(BlockEventType type, int pageNumber, Block block) {
        this.type = type;
        this.pageNumber = pageNumber;
        this.block = block;
    }

    public static BlockEvent pageStart(int pageNumber) {
        return new BlockEvent(BlockEventType.PAGE_START, pageNumber, null);
    }

    public static BlockEvent block(int pageNumber, Block block) {
        return new BlockEvent(BlockEventType.BLOCK, pageNumber, block);
    }

    public static BlockEvent pageEnd(int pageNumber) {
        return new BlockEvent(BlockEventType.PAGE_END, pageNumber, null);
    }

    public static BlockEvent done() {
        return new BlockEvent(BlockEventType.DONE, -1, null);
    }

    public BlockEventType getType() {
        return type;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public Block getBlock() {
        return block;
    }
}
