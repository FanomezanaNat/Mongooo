package com.bank.dbs.service;

import com.bank.dbs.entity.Doc;
import com.bank.dbs.exception.DocNotFoundException;
import com.bank.dbs.repository.DocRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** GET /integration-api/docs/{rootDocId}/versions (spec 5.1). */
@Service
public class VersionHistoryService {

    private final DocRepository docRepository;

    public VersionHistoryService(DocRepository docRepository) {
        this.docRepository = docRepository;
    }

    public List<Doc> getVersionChain(UUID rootDocId) {
        List<Doc> chain = docRepository.findByRootDocIdOrderByVersionNumberAsc(rootDocId);
        if (chain.isEmpty()) {
            throw new DocNotFoundException(rootDocId);
        }
        return chain;
    }

    public Doc getVersion(UUID rootDocId, int versionNumber) {
        return docRepository.findByRootDocIdAndVersionNumber(rootDocId, versionNumber)
                .orElseThrow(() -> new DocNotFoundException(rootDocId));
    }
}
