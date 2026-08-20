package com.bank.dbs.exception;

/** 422 VIRUS_DETECTED — ClamAV found malware; file is deleted immediately (AC-BE-05). */
public class VirusDetectedException extends RuntimeException {
    public VirusDetectedException(String filename, String signature) {
        super("Virus detected in '" + filename + "': " + signature);
    }
}
