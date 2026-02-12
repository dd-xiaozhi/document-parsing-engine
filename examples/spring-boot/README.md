# Spring Boot Batch Example

This example shows how to inject `DocumentEngine` from `document-spring-boot-starter`
and run batch parsing from command-line arguments.

Core flow:

1. Spring Boot auto-configures `DocumentEngine`
2. `CommandLineRunner` receives file paths from `args`
3. `parseBatch(List<File>)` parses all files in input order

Example code path:

- `src/main/java/com/document/parsing/examples/spring/SpringBatchExampleApplication.java`
