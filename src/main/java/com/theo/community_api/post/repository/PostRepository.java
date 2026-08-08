package com.theo.community_api.post.repository;

import com.theo.community_api.post.domain.Post;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        select p
        from Post p
        join fetch p.user
        where p.deletedAt is null
          and p.isBlinded = false
        order by p.id desc
    """)
    List<Post> findFirstPage(Pageable pageable);

    @Query("""
        select p
        from Post p
        join fetch p.user
        where p.id < :lastPostId
          and p.deletedAt is null
          and p.isBlinded = false
        order by p.id desc
    """)
    List<Post> findNextPage(
            @Param("lastPostId") Long lastPostId,
            Pageable pageable
    );

    @Query("""
        select p
        from Post p
        join fetch p.user
        where p.id = :postId
          and p.deletedAt is null
          and p.isBlinded = false
    """)
    Optional<Post> findByIdWithUser(
            @Param("postId") Long postId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from Post p
        where p.id = :postId
          and p.deletedAt is null
          and p.isBlinded = false
    """)
    Optional<Post> findByIdForUpdate(
            @Param("postId") Long postId
    );
}
