package com.bank.dbs.dto;

public record ChunkUploadResponse(
        int received,
        int total,
        String status // IN_PROGRESS | COMPLETED
) {}
