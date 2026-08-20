package com.bank.dbs.controller;

import com.bank.dbs.dto.MergeRequest;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.service.PdfMergeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** PDF Merge (spec 1.1 SHOULD HAVE, AC-BE-18). Integration-API scoped like other service calls. */
@RestController
@RequestMapping("/integration-api/docs/merge")
@PreAuthorize("hasAuthority('SCOPE_service')")
public class PdfMergeController {

    private final PdfMergeService pdfMergeService;

    public PdfMergeController(PdfMergeService pdfMergeService) {
        this.pdfMergeService = pdfMergeService;
    }

    @PostMapping
    public ResponseEntity<MergeResponse> merge(@Valid @RequestBody MergeRequest request) {
        Doc merged = pdfMergeService.merge(request.docIds(), request.customerId(),
                request.mergedFilename() != null ? request.mergedFilename() : "merged.pdf");
        return ResponseEntity.status(HttpStatus.CREATED).body(new MergeResponse(merged.getId()));
    }

    public record MergeResponse(UUID docId) {}
}
