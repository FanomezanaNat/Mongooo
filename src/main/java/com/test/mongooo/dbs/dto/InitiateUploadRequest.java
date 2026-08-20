package com.bank.dbs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record InitiateUploadRequest(
        @NotBlank String filename,
        @Positive long fileSize,
        @NotBlank String fileFormat,
        String docType,
        String docSubType,
        String customerId,
        @NotNull @Positive Integer totalChunks
) {}
