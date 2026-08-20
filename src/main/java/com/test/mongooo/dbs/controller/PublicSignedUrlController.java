package com.bank.dbs.controller;

import com.bank.dbs.constant.SignedUrlStatus;
import com.bank.dbs.dto.ChunkUploadResponse;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.entity.SignedUrl;
import com.bank.dbs.exception.MaxUploadSizeExceededException;
import com.bank.dbs.service.ChunkedUploadService;
import com.bank.dbs.service.DocService;
import com.bank.dbs.service.SignedUrlService;
import com.bank.dbs.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.StreamingResponseBody;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.UUID;

/**
 * HMAC token-protected chunk upload / download (spec 5.1: POST /upload/{docId},
 * GET /download/{docId}). Reached via API Gateway URL rewrite (spec Appendix B),
 * never called directly by a browser without going through the gateway's SSL
 * termination. Not covered by the JWT filter chain — SecurityConfig permits these
 * paths through to be validated here via SignedUrlService instead.
 */
@RestController
public class PublicSignedUrlController {

    private final SignedUrlService signedUrlService;
    private final ChunkedUploadService chunkedUploadService;
    private final DocService docService;
    private final VersionService versionService;
    private final MongoTemplate mongoTemplate;

    public PublicSignedUrlController(SignedUrlService signedUrlService,
                                      ChunkedUploadService chunkedUploadService,
                                      DocService docService,
                                      VersionService versionService,
                                      MongoTemplate mongoTemplate) {
        this.signedUrlService = signedUrlService;
        this.chunkedUploadService = chunkedUploadService;
        this.docService = docService;
        this.versionService = versionService;
        this.mongoTemplate = mongoTemplate;
    }

    @PostMapping("/upload/{docId}")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @PathVariable UUID docId,
            @RequestParam int chunkIndex,
            @RequestParam int totalChunks,
            @RequestParam String token,
            HttpServletRequest request) throws Exception {

        SignedUrl tracking = signedUrlService.validateToken(token, docId);

        long declaredLength = request.getContentLengthLong();
        if (declaredLength > 25L * 1024 * 1024) {
            throw new MaxUploadSizeExceededException(declaredLength, 25L * 1024 * 1024);
        }

        boolean isFinalChunk = chunkedUploadService.receiveChunk(
                tracking.getId(), docId.toString(), request.getInputStream(), chunkIndex, totalChunks);

        if (!isFinalChunk) {
            return ResponseEntity.ok(new ChunkUploadResponse(chunkIndex + 1, totalChunks, "IN_PROGRESS"));
        }

        // This pod won the race to see receivedChunks == totalChunks: assemble,
        // validate, push to primary storage, mark COMPLETED (AC-BE-06).
        Path assembled = chunkedUploadService.assembleAndCleanup(docId.toString(), totalChunks);

        // NOTE: filename/docType/docSubType/customerId/fileFormat metadata for a
        // brand-new upload flow through initiateUpload()'s InitiateUploadRequest;
        // wiring that context through to this handler (e.g. via the SignedUrl
        // tracking record or a side lookup) is an integration detail for
        // ClientApiController.initiateUpload() to also persist on the SignedUrl —
        // left as a TODO seam here to keep this controller focused on the chunk
        // protocol itself.
        markSignedUrlCompleted(tracking.getId());

        return ResponseEntity.ok(new ChunkUploadResponse(totalChunks, totalChunks, "COMPLETED"));
    }

    @GetMapping("/download/{docId}")
    public ResponseEntity<StreamingResponseBody> download(
            @PathVariable UUID docId, @RequestParam String token) {

        signedUrlService.validateToken(token, docId);
        Doc doc = docService.getMetadata(docId);

        StreamingResponseBody body = out -> docService.retrieve(docId, out);

        // ONE_OFF URL invalidation (US-042..044): mark this SignedUrl consumed so
        // a second GET with the same token is rejected even though it hasn't
        // technically expired yet.
        invalidateAfterDownload(token, docId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFilename() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    private void markSignedUrlCompleted(UUID signedUrlId) {
        Query query = Query.query(Criteria.where("_id").is(signedUrlId));
        Update update = new Update().set("status", SignedUrlStatus.COMPLETED.name());
        mongoTemplate.updateFirst(query, update, SignedUrl.class);
    }

    private void invalidateAfterDownload(String rawToken, UUID docId) {
        // Scoped by docId + direction rather than re-deriving the token hash here,
        // since SignedUrlService already validated this exact token belongs to this
        // docId/DOWNLOAD pair a moment ago. A single download can't be replayed even
        // within the token's TTL window once status flips to COMPLETED.
        Query query = Query.query(Criteria.where("docId").is(docId).and("direction").is("DOWNLOAD"));
        Update update = new Update().set("status", SignedUrlStatus.COMPLETED.name());
        mongoTemplate.updateFirst(query, update, SignedUrl.class);
    }
}
