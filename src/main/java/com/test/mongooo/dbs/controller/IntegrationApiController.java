package com.bank.dbs.controller;

import com.bank.dbs.dto.ArchiveTaskRequest;
import com.bank.dbs.dto.ArchiveTaskResponse;
import com.bank.dbs.dto.VersionHistoryResponse;
import com.bank.dbs.dto.VersionInfo;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.exception.MaxUploadSizeExceededException;
import com.bank.dbs.service.ArchivalTaskService;
import com.bank.dbs.service.DocService;
import com.bank.dbs.service.VersionHistoryService;
import jakarta.validation.Valid;
import org.springframework.core.io.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Service-to-service only endpoints (spec 5.1). Secured by @PreAuthorize with the
 * service-JWT scope configured in SecurityConfig — consuming applications
 * (Non-Indiv Onboarding, Trade Finance/Catalyst, Individual/SME Onboarding,
 * Scanning & Archival) call these, never end-user browsers directly.
 */
@RestController
@RequestMapping("/integration-api/docs")
@PreAuthorize("hasAuthority('SCOPE_service')")
public class IntegrationApiController {

    private static final long MAX_FILE_SIZE_BYTES = 25L * 1024 * 1024;

    private final DocService docService;
    private final ArchivalTaskService archivalTaskService;
    private final VersionHistoryService versionHistoryService;

    public IntegrationApiController(DocService docService,
                                     ArchivalTaskService archivalTaskService,
                                     VersionHistoryService versionHistoryService) {
        this.docService = docService;
        this.archivalTaskService = archivalTaskService;
        this.versionHistoryService = versionHistoryService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoreResponse> store(
            @RequestParam("file") MultipartFile file,
            @RequestParam("repoId") String repoId,
            @RequestParam(value = "docType", required = false) String docType,
            @RequestParam(value = "customerId", required = false) String customerId) throws IOException {

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new MaxUploadSizeExceededException(file.getSize(), MAX_FILE_SIZE_BYTES);
        }

        Path staged = Files.createTempFile("dbs-int-upload-", ".tmp");
        file.transferTo(staged);
        String fileFormat = extractExtension(file.getOriginalFilename());

        Doc doc = docService.store(staged, file.getOriginalFilename(), file.getSize(), fileFormat,
                docType, null, customerId, null);

        return ResponseEntity.status(HttpStatus.CREATED).body(new StoreResponse(doc.getId()));
    }

    @GetMapping("/{docId}")
    public ResponseEntity<StreamingResponseBody> get(@PathVariable UUID docId) {
        Doc doc = docService.getMetadata(docId);
        StreamingResponseBody body = out -> docService.retrieve(docId, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(@PathVariable UUID docId) {
        docService.delete(docId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{docId}/archive-task")
    public ResponseEntity<ArchiveTaskResponse> createArchiveTask(
            @PathVariable UUID docId, @Valid @RequestBody ArchiveTaskRequest request) {
        UUID taskId = archivalTaskService.createArchivalTask(
                docId, request.docType(), request.docSubType(), request.customerId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ArchiveTaskResponse(taskId));
    }

    @GetMapping("/{rootDocId}/versions")
    public ResponseEntity<VersionHistoryResponse> getVersions(@PathVariable UUID rootDocId) {
        List<VersionInfo> versions = versionHistoryService.getVersionChain(rootDocId).stream()
                .map(d -> new VersionInfo(d.getId(), d.getVersionNumber(), d.getDocState(),
                        d.isCurrent(), d.getArchiveDocUri() != null, d.getDtCreated()))
                .toList();
        return ResponseEntity.ok(new VersionHistoryResponse(rootDocId, versions));
    }

    @GetMapping("/{rootDocId}/versions/{vNum}")
    public ResponseEntity<StreamingResponseBody> getVersionBinary(
            @PathVariable UUID rootDocId, @PathVariable int vNum) {
        Doc version = versionHistoryService.getVersion(rootDocId, vNum);
        StreamingResponseBody body = out -> docService.retrieve(version.getId(), out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + version.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    public record StoreResponse(UUID docId) {}
}
