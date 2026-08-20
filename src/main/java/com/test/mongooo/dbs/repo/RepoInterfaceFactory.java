package com.bank.dbs.repo;

import com.bank.dbs.entity.RepoConfig;
import com.bank.dbs.exception.RepoInstantiationException;
import com.bank.dbs.repository.RepoConfigRepository;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the concrete RepoInterface bean for a given repoId ("S3_PRIMARY",
 * "CMOD_ARCHIVE", "FS_STAGING", ...) by looking up repo_configs.beanName and
 * fetching that Spring bean from the ApplicationContext.
 *
 * A small in-memory cache avoids a Mongo round-trip on every store/get call; entries
 * are invalidated on... they simply aren't — repo_configs is operational
 * configuration data that changes only via a deliberate deploy/config change, so a
 * process-lifetime cache is an acceptable tradeoff (documented so a future
 * maintainer doesn't assume live cache invalidation exists).
 */
@Component
public class RepoInterfaceFactory {

    private final RepoConfigRepository repoConfigRepository;
    private final ApplicationContext applicationContext;
    private final Map<String, RepoInterface> cache = new ConcurrentHashMap<>();

    public RepoInterfaceFactory(RepoConfigRepository repoConfigRepository, ApplicationContext applicationContext) {
        this.repoConfigRepository = repoConfigRepository;
        this.applicationContext = applicationContext;
    }

    public RepoInterface resolve(String repoId) {
        return cache.computeIfAbsent(repoId, this::instantiate);
    }

    private RepoInterface instantiate(String repoId) {
        RepoConfig config = repoConfigRepository.findById(repoId)
                .orElseThrow(() -> new RepoInstantiationException(repoId,
                        new IllegalStateException("No repo_configs entry for repoId=" + repoId)));

        if (!config.isActive()) {
            throw new RepoInstantiationException(repoId, new IllegalStateException("Repo is marked inactive"));
        }

        try {
            return (RepoInterface) applicationContext.getBean(config.getBeanName());
        } catch (Exception e) {
            throw new RepoInstantiationException(repoId, e);
        }
    }
}
