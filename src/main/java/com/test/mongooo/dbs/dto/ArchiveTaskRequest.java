package com.bank.dbs.dto;

public record ArchiveTaskRequest(
        String docType,
        String docSubType,
        String customerId
) {}
