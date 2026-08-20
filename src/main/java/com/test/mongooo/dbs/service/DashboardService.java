package com.bank.dbs.service;

import com.bank.dbs.cqrs.DocsView;
import com.bank.dbs.cqrs.DocsViewRepository;
import com.bank.dbs.dto.DocSummary;
import com.bank.dbs.entity.ApplicationDoc;
import com.bank.dbs.repository.ApplicationDocRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * GET /client-api/applications/{appId}/docs (spec 5.1). Reads go against the CQRS
 * `docs_view` read model, not the `docs` write-side collection, per spec 6.1 —
 * ApplicationDoc gives us the applicationId -> rootDocId mapping, DocsView gives us
 * the fast, denormalised current-version summary for display.
 */
@Service
public class DashboardService {

    private final ApplicationDocRepository applicationDocRepository;
    private final DocsViewRepository docsViewRepository;

    public DashboardService(ApplicationDocRepository applicationDocRepository, DocsViewRepository docsViewRepository) {
        this.applicationDocRepository = applicationDocRepository;
        this.docsViewRepository = docsViewRepository;
    }

    public Page<DocSummary> listDocuments(String applicationId, String docTypeFilter, Pageable pageable) {
        List<ApplicationDoc> links = applicationDocRepository.findByApplicationId(applicationId);

        List<DocSummary> summaries = links.stream()
                .filter(link -> docTypeFilter == null || docTypeFilter.equalsIgnoreCase(link.getDocType()))
                .map(link -> docsViewRepository.findById(resolveCurrentDocId(link)))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toSummary)
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), summaries.size());
        List<DocSummary> pageContent = start >= summaries.size() ? List.of() : summaries.subList(start, end);

        return new PageImpl<>(pageContent, pageable, summaries.size());
    }

    private UUID resolveCurrentDocId(ApplicationDoc link) {
        // ApplicationDoc.docId tracks the specific version last linked; DocsView is
        // keyed by the doc's own _id (per-version), so this looks up that exact
        // record. Callers wanting only the *current* version should ensure the
        // linking flow keeps ApplicationDoc.docId pointed at the latest version's id
        // (VersionService callers are responsible for that update on replacement).
        return link.getDocId();
    }

    private DocSummary toSummary(DocsView view) {
        return new DocSummary(
                view.getId(),
                view.getFilename(),
                view.getDocType(),
                view.getDocSubType(),
                view.getDocState(),
                view.isArchived(),
                view.getDtCreated()
        );
    }
}
