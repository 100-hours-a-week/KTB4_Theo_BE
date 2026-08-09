package com.theo.community_api.post.domain;

import com.theo.community_api.common.time.UtcDateTimes;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_revision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 수정이력 N : 게시글 1
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postId", nullable = false)
    private Post post;

    @Column(nullable = false, length = 26)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime updatedAt; // 수정이력이 저장된 시각

    public PostRevision(Post post, String title, String content) {
        this.post = post;
        this.title = title;
        this.content = content;
        this.updatedAt = UtcDateTimes.now();
    }

    public static PostRevision from(Post post) {
        return new PostRevision(
                post,
                post.getTitle(),
                post.getContent()
        );
    }
}
