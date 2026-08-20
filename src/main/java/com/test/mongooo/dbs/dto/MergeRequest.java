package com.bank.dbs.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record MergeRequest(
        @NotEmpty @Size(max = 10) List<UUID> docIds,
        String customerId,
        String mergedFilename
) {}
