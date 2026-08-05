package com.theo.community_api.draft.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "draft_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DraftImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 여러 이미지가 하나의 임시글에 속한다
    @ManyToOne
    @JoinColumn(name = "draftId", nullable = false)
    private Draft draft;

    @Column(name = "image_key", nullable = false, length = 500)
    private String imageKey;

    @Column(nullable = false)
    private Integer imageOrder;

    public DraftImage(Draft draft, String imageKey, Integer imageOrder){
        this.draft = draft;
        this.imageKey = imageKey;
        this.imageOrder = imageOrder;
    }
}
