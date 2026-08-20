package com.bank.dbs.dto;

import java.util.List;
import java.util.UUID;

public record VersionHistoryResponse(
        UUID rootDocId,
        List<VersionInfo> versions
) {}
