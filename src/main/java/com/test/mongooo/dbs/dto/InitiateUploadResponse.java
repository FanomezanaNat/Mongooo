package com.bank.dbs.dto;

import java.time.Instant;
import java.util.UUID;

public record InitiateUploadResponse(
        UUID docId,
        String uploadUrl,
        String token,
        Instant expiresAt
) {}
