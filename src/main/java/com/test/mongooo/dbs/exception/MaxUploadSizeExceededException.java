package com.bank.dbs.exception;

/** 413 FILE_TOO_LARGE — file exceeds the 25 MB limit. */
public class MaxUploadSizeExceededException extends RuntimeException {
    public MaxUploadSizeExceededException(long actualBytes, long maxBytes) {
        super("File size " + actualBytes + " bytes exceeds maximum allowed " + maxBytes + " bytes");
    }
}
