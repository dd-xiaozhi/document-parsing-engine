package com.document.parsing.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Table {
    private final String id;
    private final int pageNumber;
    private final List<List<String>> rows;

    public Table(String id, int pageNumber, List<List<String>> rows) {
        this.id = id;
        this.pageNumber = pageNumber;
        this.rows = new ArrayList<>(Objects.requireNonNullElse(rows, List.of()));
    }

    public String getId() {
        return id;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public List<List<String>> getRows() {
        return rows;
    }
}
