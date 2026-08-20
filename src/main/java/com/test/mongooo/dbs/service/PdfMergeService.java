package com.bank.dbs.service;

import com.bank.dbs.constant.DocState;
import com.bank.dbs.entity.Doc;
import com.bank.dbs.exception.DocNotFoundException;
import com.bank.dbs.repo.RepoInterface;
import com.bank.dbs.repo.RepoInterfaceFactory;
import com.bank.dbs.repository.DocRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * PDF Merge (spec 1.1 "SHOULD HAVE" capability, AC-BE-18): combines up to 10 PDFs
 * via PDFBox PDFMergerUtility; stored as a new version; inputs set to REPLACED;
 * aborts with 422 if any input is encrypted.
 *
 * R07 (risk register): PDFMergerUtility can produce corrupted output for
 * scanned/image-only PDFs in some PDFBox versions — if that surfaces in testing,
 * fall back to the lower-level PDFBox page-append API (iterate source pages and
 * PDPageContentStream-copy them into the target document) instead of
 * PDFMergerUtility.mergeDocuments().
 */
@Service
public class PdfMergeService {

    private static final Logger log = LoggerFactory.getLogger(PdfMergeService.class);
    private static final int MAX_INPUTS = 10;
    private static final String PRIMARY_REPO_ID = "S3_PRIMARY";

    private final DocRepository docRepository;
    private final RepoInterfaceFactory repoInterfaceFactory;

    public PdfMergeService(DocRepository docRepository, RepoInterfaceFactory repoInterfaceFactory) {
        this.docRepository = docRepository;
        this.repoInterfaceFactory = repoInterfaceFactory;
    }

    @Transactional("transactionManager")
    public Doc merge(List<UUID> inputDocIds, String customerId, String mergedFilename) {
        if (inputDocIds.isEmpty() || inputDocIds.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("PDF merge accepts between 1 and " + MAX_INPUTS + " input documents");
        }

        List<Doc> inputs = inputDocIds.stream()
                .map(id -> docRepository.findById(id).orElseThrow(() -> new DocNotFoundException(id)))
                .toList();

        RepoInterface primaryRepo = repoInterfaceFactory.resolve(PRIMARY_REPO_ID);

        byte[] mergedBytes = mergeAndValidateNotEncrypted(inputs, primaryRepo);

        String repoDocId;
        try (ByteArrayInputStream in = new ByteArrayInputStream(mergedBytes)) {
            repoDocId = primaryRepo.store(UUID.randomUUID().toString(), in, mergedBytes.length, "application/pdf");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store merged PDF", e);
        }

        Doc merged = new Doc();
        merged.setId(UUID.randomUUID());
        merged.setRootDocId(UUID.randomUUID()); // merged output starts a new version chain
        merged.setVersionNumber(1);
        merged.setCurrent(true);
        merged.setDocState(DocState.ACTIVE);
        merged.setDocUri(new com.bank.dbs.entity.DocUri(PRIMARY_REPO_ID, repoDocId));
        merged.setFilename(mergedFilename);
        merged.setFileSize(mergedBytes.length);
        merged.setFileFormat("pdf");
        merged.setCustomerId(customerId);
        merged.setDocType(inputs.get(0).getDocType());
        merged.setDocSubType(inputs.get(0).getDocSubType());
        merged.setDtCreated(Instant.now());
        merged.setDtUpdated(Instant.now());
        Doc saved = docRepository.save(merged);

        // Mark all inputs REPLACED, all within the same transaction as the merged
        // doc's insert (AC-BE-18).
        for (Doc input : inputs) {
            input.setDocState(DocState.REPLACED);
            input.setCurrent(false);
            input.setDtUpdated(Instant.now());
            docRepository.save(input);
        }

        log.info("Merged {} input PDFs into new docId={}", inputs.size(), saved.getId());
        return saved;
    }

    /**
     * Two passes over the inputs, deliberately kept separate for clarity and
     * safety: (1) open each PDF just far enough to check isEncrypted() and abort
     * the whole merge before any output is produced if one fails (AC-BE-18
     * "aborts with 422 if any input encrypted"); (2) the actual byte-level merge
     * via PDFMergerUtility once every input has passed the check.
     */
    private byte[] mergeAndValidateNotEncrypted(List<Doc> inputs, RepoInterface primaryRepo) {
        for (Doc input : inputs) {
            byte[] content = readFullySafely(primaryRepo, input);
            try (PDDocument pd = Loader.loadPDF(content)) {
                if (pd.isEncrypted()) {
                    throw new com.bank.dbs.exception.EncryptedDocumentException(input.getFilename());
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to open input PDF for merge validation: "
                        + input.getFilename(), e);
            }
        }

        try {
            return mergeViaStreams(inputs, primaryRepo);
        } catch (IOException e) {
            throw new IllegalStateException("PDF merge failed", e);
        }
    }

    private byte[] readFullySafely(RepoInterface repo, Doc doc) {
        try {
            return readFully(repo, doc);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read input PDF: " + doc.getFilename(), e);
        }
    }

    private byte[] mergeViaStreams(List<Doc> inputs, RepoInterface primaryRepo) throws IOException {
        PDFMergerUtility merger = new PDFMergerUtility();
        ByteArrayOutputStream mergedOut = new ByteArrayOutputStream();
        merger.setDestinationStream(mergedOut);

        for (Doc input : inputs) {
            byte[] content = readFully(primaryRepo, input);
            merger.addSource(new ByteArrayInputStream(content));
        }

        merger.mergeDocuments(org.apache.pdfbox.io.MemoryUsageSetting.setupMainMemoryOnly());
        return mergedOut.toByteArray();
    }

    private byte[] readFully(RepoInterface repo, Doc doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        repo.get(doc.getDocUri().getRepoDocId(), out);
        return out.toByteArray();
    }
}
