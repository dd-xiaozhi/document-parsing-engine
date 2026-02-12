package com.document.parsing.examples.spring;

import com.document.parsing.core.engine.DocumentEngine;
import com.document.parsing.core.model.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
public class SpringBatchExampleApplication {

    @Bean
    CommandLineRunner demo(DocumentEngine engine) {
        return args -> {
            if (args.length == 0) {
                System.out.println("Usage: java ... <file1> <file2> ...");
                return;
            }

            List<File> files = Arrays.stream(args)
                .map(File::new)
                .toList();

            List<Document> documents = engine.parseBatch(files);
            for (int i = 0; i < documents.size(); i++) {
                Document document = documents.get(i);
                System.out.println("[" + files.get(i).getName() + "] " + document.getRawText());
            }
        };
    }
}
