package com.bank.dbs.dto;

import com.bank.dbs.constant.DocState;

import java.time.Instant;
import java.util.UUID;

public record DocDetail(
        UUID docId,
        UUID rootDocId,
        String filename,
        long fileSize,
        String fileFormat,
        String docType,
        String docSubType,
        String customerId,
        DocState docState,
        boolean archived,
        int versionNumber,
        Instant dtCreated,
        Instant dtUpdated
) {}
