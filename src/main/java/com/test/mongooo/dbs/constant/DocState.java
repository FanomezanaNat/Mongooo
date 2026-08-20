package com.bank.dbs.constant;

/**
 * Lifecycle state of a {@code docs} record.
 * PENDING -> ACTIVE happens once upload assembly + validation completes.
 * REPLACED is set on the previous "current" version when a new version is created.
 */
public enum DocState {
    PENDING,
    ACTIVE,
    REPLACED
}
