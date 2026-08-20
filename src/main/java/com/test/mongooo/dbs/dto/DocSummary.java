package com.bank.dbs.dto;

import com.bank.dbs.constant.DocState;

import java.time.Instant;
import java.util.UUID;

public record DocSummary(
        UUID docId,
        String filename,
        String docType,
        String docSubType,
        DocState docState,
        boolean archived,
        Instant dtCreated
) {}
