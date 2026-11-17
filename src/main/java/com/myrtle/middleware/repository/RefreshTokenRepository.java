package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.RefreshSubjectType;
import com.myrtle.middleware.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revoked = true, t.revokedAt = :now
             where t.id = :id and t.revoked = false
            """)
    int revokeIfActive(@Param("id") String id, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update RefreshToken t
               set t.revoked = true, t.revokedAt = :now
             where t.subjectType = :subjectType
               and t.subjectId = :subjectId
               and t.revoked = false
            """)
    int revokeActiveForSubject(
            @Param("subjectType") RefreshSubjectType subjectType,
            @Param("subjectId") String subjectId,
            @Param("now") Instant now
    );

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            delete from refresh_tokens
             where id in (
                   select id
                     from refresh_tokens
                    where expires_at < :cutoff
                       or (revoked = true and revoked_at < :cutoff)
                    limit :batchSize
             )
            """, nativeQuery = true)
    int deletePurgeableBatch(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
