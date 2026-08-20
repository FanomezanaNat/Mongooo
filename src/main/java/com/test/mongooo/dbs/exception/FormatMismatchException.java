package com.bank.dbs.exception;

/** 422 FORMAT_MISMATCH — magic bytes do not match declared format (AC-BE-04). */
public class FormatMismatchException extends RuntimeException {
    public FormatMismatchException(String declaredFormat, String detectedFormat) {
        super("File content does not match declared format. Declared: " + declaredFormat
                + ", detected: " + detectedFormat);
    }
}
