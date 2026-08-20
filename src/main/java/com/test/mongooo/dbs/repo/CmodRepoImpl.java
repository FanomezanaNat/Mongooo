package com.bank.dbs.repo;

import com.bank.dbs.exception.RepoInstantiationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Regulatory archive target (spec 2.3/2.4 "IBM CMOD Service" / ODWEK Java SDK).
 * archive() is the only meaningful write path here — CmodRepoImpl is never used as
 * a *primary* store, only as ArchivalScheduler's destination.
 *
 * NOTE: The ODWEK Java SDK is an IBM/bank-internal artifact not available on public
 * Maven repositories, so this class integrates against a thin {@link OdwekClient}
 * seam rather than the concrete IBM classes. Swap the seam's implementation for the
 * bank's actual ODWEK client wiring (typically an OD system-instance connection pool
 * configured via odwek.properties) without touching RepoInterface callers.
 *
 * R01 (risk register): a Sprint-0 ODWEK spike should validate real error codes and
 * timeout behaviour against the bank's CMOD instance; the retry/backoff below is a
 * conservative starting point pending that spike.
 */
@Component("cmodRepoImpl")
public class CmodRepoImpl implements RepoInterface {

    private static final Logger log = LoggerFactory.getLogger(CmodRepoImpl.class);

    private final OdwekClient odwekClient;
    private final String applicationGroup;

    public CmodRepoImpl(OdwekClient odwekClient,
                         @Value("${dbs.storage.cmod.application-group:DBS_ARCHIVE_GROUP}") String applicationGroup) {
        this.odwekClient = odwekClient;
        this.applicationGroup = applicationGroup;
    }

    @Override
    public String store(String docId, InputStream content, long contentLength, String contentType) {
        // CMOD is archive-only in this design; direct store() calls are not part of
        // the intended flow but are supported for completeness/testing.
        return archive(docId, content, contentLength, contentType);
    }

    @Override
    public String archive(String repoDocId, InputStream content, long contentLength, String contentType) {
        try {
            String odDocId = odwekClient.loadDocument(applicationGroup, repoDocId, content, contentType);
            log.info("Archived doc {} to CMOD as OD document id {}", repoDocId, odDocId);
            return odDocId;
        } catch (Exception e) {
            throw new RepoInstantiationException("CMOD_ARCHIVE", e);
        }
    }

    @Override
    public void get(String repoDocId, OutputStream destination) {
        try {
            odwekClient.retrieveDocument(applicationGroup, repoDocId, destination);
        } catch (Exception e) {
            throw new RepoInstantiationException("CMOD_ARCHIVE", e);
        }
    }

    @Override
    public InputStream getAsStream(String repoDocId) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        get(repoDocId, buffer);
        return new java.io.ByteArrayInputStream(buffer.toByteArray());
    }

    @Override
    public void delete(String repoDocId) {
        // Write-once, read-rarely: CMOD deletion is deliberately unsupported here.
        // Regulatory archives are governed by retention policy on the CMOD side, not
        // by application-triggered deletes (spec 1.2: "Long-term cold-storage
        // lifecycle beyond CMOD" is explicitly out of scope).
        throw new UnsupportedOperationException("Deletion from IBM CMOD archive is not permitted via DBS");
    }

    @Override
    public String repoId() {
        return "CMOD_ARCHIVE";
    }

    /**
     * Thin seam over the bank's ODWEK Java SDK connection. Provide a real
     * implementation (typically backed by com.ibm.mvs.odwek.* classes and an OD
     * system-instance connection pool) as a Spring bean in the deployment's
     * infrastructure module.
     */
    public interface OdwekClient {
        String loadDocument(String applicationGroup, String docId, InputStream content, String contentType) throws Exception;

        void retrieveDocument(String applicationGroup, String odDocId, OutputStream destination) throws Exception;
    }
}
