package com.bank.dbs.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMetadataRequest(
        @NotBlank String docType,
        @NotBlank String docSubType
) {}
