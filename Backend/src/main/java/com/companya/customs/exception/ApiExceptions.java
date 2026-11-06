package com.companya.customs.exception;

public final class ApiExceptions {
    private ApiExceptions() { }
    public static class NotFound extends RuntimeException { public NotFound(String message) { super(message); } }
    public static class Validation extends RuntimeException { public Validation(String message) { super(message); } }
    public static class ExportBlocked extends RuntimeException { public ExportBlocked(String message) { super(message); } }
}
