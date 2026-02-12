package com.document.parsing.examples.basic;

import com.document.parsing.core.engine.DocumentEngine;

import java.io.File;

public class BasicExample {
    public static void main(String[] args) {
        DocumentEngine engine = DocumentEngine.builder()
            .autoRegister()
            .build();

        var doc = engine.parse(new File(args.length > 0 ? args[0] : "test.pdf"));
        System.out.println(doc.getRawText());
    }
}
