package com.bank.dbs.controller;

import com.bank.dbs.dto.DocDetail;
import com.bank.dbs.dto.DocSummary;
import com.bank.dbs.dto.InitiateUploadRequest;
import com.bank.dbs.dto.InitiateUploadResponse;
import com.bank.dbs.dto.UpdateMetadataRequest;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.service.DashboardService;
import com.bank.dbs.service.DocService;
import com.bank.dbs.service.EntitlementService;
import com.bank.dbs.service.SignedUrlService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operator-facing endpoints consumed by the Angular UI (spec 4.3 Services & API
 * Calls). Every method starts with an entitlement check, per the architecture
 * diagram's repeated note: "check user/customer_cir is allowed to
 * add/replace/query/delete a supporting doc to application_id".
 */
@RestController
@RequestMapping("/client-api/applications/{appId}/docs")
public class ClientApiController {

    private final DocService docService;
    private final DashboardService dashboardService;
    private final SignedUrlService signedUrlService;
    private final EntitlementService entitlementService;
    private final String gatewayPublicBaseUrl;

    public ClientApiController(DocService docService,
                                DashboardService dashboardService,
                                SignedUrlService signedUrlService,
                                EntitlementService entitlementService,
                                @Value("${dbs.gateway.public-base-url}") String gatewayPublicBaseUrl) {
        this.docService = docService;
        this.dashboardService = dashboardService;
        this.signedUrlService = signedUrlService;
        this.entitlementService = entitlementService;
        this.gatewayPublicBaseUrl = gatewayPublicBaseUrl;
    }

    /** initiateUpload(): returns { docId, uploadUrl, token, expiresAt } (AC-BE-02). */
    @PostMapping
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            Authentication auth,
            @PathVariable String appId,
            @Valid @RequestBody InitiateUploadRequest request) {

        entitlementService.assertCanAccessApplication(auth, appId);

        UUID docId = UUID.randomUUID();
        SignedUrlService.IssuedToken issued = signedUrlService.generateUploadUrl(docId, request.totalChunks());

        String uploadUrl = gatewayPublicBaseUrl + "/upload/" + docId;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new InitiateUploadResponse(docId, uploadUrl, issued.token(), issued.expiresAt()));
    }

    @GetMapping
    public ResponseEntity<Page<DocSummary>> listDocuments(
            Authentication auth,
            @PathVariable String appId,
            @RequestParam(required = false) String docType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        entitlementService.assertCanAccessApplication(auth, appId);
        Page<DocSummary> result = dashboardService.listDocuments(appId, docType, PageRequest.of(page, size));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{docId}")
    public ResponseEntity<DocDetail> getDetail(
            Authentication auth, @PathVariable String appId, @PathVariable UUID docId) {
        entitlementService.assertCanAccessApplication(auth, appId);
        Doc doc = docService.getMetadata(docId);
        return ResponseEntity.ok(toDetail(doc));
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Void> delete(
            Authentication auth, @PathVariable String appId, @PathVariable UUID docId) {
        entitlementService.assertCanAccessApplication(auth, appId);
        docService.delete(docId); // throws ArchivedDocumentException -> 403 if archived (AC-BE-09)
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{docId}/metadata")
    public ResponseEntity<DocDetail> updateMetadata(
            Authentication auth, @PathVariable String appId, @PathVariable UUID docId,
            @Valid @RequestBody UpdateMetadataRequest request) {
        entitlementService.assertCanAccessApplication(auth, appId);
        Doc updated = docService.updateMetadata(docId, request.docType(), request.docSubType());
        return ResponseEntity.ok(toDetail(updated));
    }

    private DocDetail toDetail(Doc doc) {
        return new DocDetail(
                doc.getId(), doc.getRootDocId(), doc.getFilename(), doc.getFileSize(), doc.getFileFormat(),
                doc.getDocType(), doc.getDocSubType(), doc.getCustomerId(), doc.getDocState(),
                doc.getArchiveDocUri() != null, doc.getVersionNumber(), doc.getDtCreated(), doc.getDtUpdated());
    }
}
