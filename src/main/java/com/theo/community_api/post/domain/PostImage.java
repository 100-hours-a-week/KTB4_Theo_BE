package com.theo.community_api.post.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "post_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 게시글 사진은 게시글에 소속된다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postId", nullable = false)
    private Post post;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    @Column(nullable = false)
    private Integer imageOrder; // 게시글 사진 순서를 저장

    public PostImage(Post post, String imageKey, Integer imageOrder) {
        this.post = post;
        this.imageKey = imageKey;
        this.imageOrder = imageOrder;
    }

    public void updateOrder(Integer imageOrder) {
        this.imageOrder = imageOrder;
    }
}
