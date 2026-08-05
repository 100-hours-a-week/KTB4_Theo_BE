package com.theo.community_api.post.repository;

import com.theo.community_api.post.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    // 게시글 상세 조회 시 image_order id 기준 오름차순으로 조회
    List<PostImage> findAllByPost_IdOrderByImageOrderAsc(Long postId);

    @Query("select pi.imageKey from PostImage pi")
    List<String> findAllImageKeys();

    // 게시글 수정/삭제 시 기존 이미지 모두 삭제
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query("""
    delete from PostImage pi
    where pi.post.id = :postId
""")
    void deleteAllByPostIdInBulk(@Param("postId") Long postId);
}
