package com.theo.community_api.draft.repository;

import com.theo.community_api.draft.domain.DraftImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DraftImageRepository extends JpaRepository<DraftImage, Long> {
    List<DraftImage> findAllByDraftIdOrderByImageOrderAsc(Long draftId);

    @Query("select di.imageKey from DraftImage di")
    List<String> findAllImageKeys();

    void deleteAllByDraftId(Long draftId);

    @Modifying(flushAutomatically = true)
    @Query("""
        delete from DraftImage di
        where di.draft.id = :draftId
    """)
    void deleteAllByDraftIdInBulk(@Param("draftId") Long draftId);
}
