package com.document.parsing.core.exception;

public class ParserTimeoutException extends ParseException {
    public ParserTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
