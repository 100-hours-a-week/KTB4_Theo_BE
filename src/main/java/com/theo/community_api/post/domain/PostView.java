package com.theo.community_api.post.domain;

import com.theo.community_api.common.time.UtcDateTimes;
import com.theo.community_api.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_view",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_POST_VIEW_POST_USER",
                        columnNames = {"post_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime viewedAt;

    public PostView(Post post, User user) {
        this.post = post;
        this.user = user;
        this.viewedAt = UtcDateTimes.now();
    }

    // 조회수 증가여부 확인 (조회 시점으로부터 24시간이상 지난 시점이여야 함)
    public boolean canIncreaseViewCount(LocalDateTime now) {
        return viewedAt.plusHours(24).isBefore(now)
                || viewedAt.plusHours(24).isEqual(now);
    }

    public void updateViewedAt(LocalDateTime now) {
        this.viewedAt = now;
    }
}
