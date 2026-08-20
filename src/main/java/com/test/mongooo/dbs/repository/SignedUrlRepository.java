package com.bank.dbs.repository;

import com.bank.dbs.constant.SignedUrlStatus;
import com.bank.dbs.entity.SignedUrl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SignedUrlRepository extends MongoRepository<SignedUrl, UUID> {

    /** O(1) token validation lookup — uses the unique idx_tokenHash_unique index. */
    Optional<SignedUrl> findByTokenHash(String tokenHash);

    List<SignedUrl> findByDocIdAndStatus(UUID docId, SignedUrlStatus status);

    /** Link-expiry cleanup job — uses idx_status_expiresAt. */
    @Query("{ 'status': { $in: ['ISSUED', 'IN_PROGRESS'] }, 'expiresAt': { $lte: ?0 } }")
    List<SignedUrl> findExpiredButNotYetMarked(Instant now);
}
