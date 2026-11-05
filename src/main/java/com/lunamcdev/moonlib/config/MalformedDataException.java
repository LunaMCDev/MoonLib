package com.lunamcdev.moonlib.config;

import java.io.IOException;

/**
 * Thrown when a YAML or JSON document cannot be parsed. For JSON the message includes the line and
 * column of the problem.
 */
public class MalformedDataException extends IOException {

    /** Serialisation version. */
    private static final long serialVersionUID = 1L;

    /** The 1-based line of the error, or -1 if unknown. */
    private final int line;
    /** The 1-based column of the error, or -1 if unknown. */
    private final int column;

    /**
     * Creates an exception without a position.
     *
     * @param message the description
     * @param cause   the underlying error, may be {@code null}
     */
    public MalformedDataException(String message, Throwable cause) {
        super(message, cause);
        this.line = -1;
        this.column = -1;
    }

    /**
     * Creates an exception at a position in the document.
     *
     * @param message the description
     * @param line    the 1-based line
     * @param column  the 1-based column
     */
    public MalformedDataException(String message, int line, int column) {
        super(message + " (line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the line of the error.
     *
     * @return the 1-based line, or -1 if unknown
     */
    public int line() {
        return line;
    }

    /**
     * Returns the column of the error.
     *
     * @return the 1-based column, or -1 if unknown
     */
    public int column() {
        return column;
    }
}
