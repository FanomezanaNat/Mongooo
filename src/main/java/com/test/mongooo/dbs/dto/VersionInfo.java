package com.bank.dbs.dto;

import com.bank.dbs.constant.DocState;

import java.time.Instant;
import java.util.UUID;

public record VersionInfo(
        UUID docId,
        int versionNumber,
        DocState docState,
        boolean isCurrent,
        boolean archived,
        Instant dtCreated
) {}
