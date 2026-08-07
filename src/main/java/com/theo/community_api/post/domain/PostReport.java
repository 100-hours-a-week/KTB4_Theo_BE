package com.theo.community_api.post.domain;

import com.theo.community_api.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "post_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_POST_REPORT_POST_USER",
                        columnNames = {"post_id", "user_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 신고 대상 게시글
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 신고한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 신고 사유
    @Column(nullable = false, length = 300)
    private String reason;

    // 신고 접수 시각
    @Column(nullable = false)
    private LocalDateTime reportedAt;

    // 신고 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    private PostReportStatus status;

    // 관리자 처리 시각
    private LocalDateTime processedAt;

    // 관리자 처리 메모
    @Column(length = 255)
    private String adminMemo;

    private PostReport(Post post, User user, String reason) {
        this.post = post;
        this.user = user;
        this.reason = reason;
        this.reportedAt = LocalDateTime.now();
        this.status = PostReportStatus.PENDING;
    }

    public static PostReport create(Post post, User user, String reason) {
        return new PostReport(post, user, reason);
    }

    public void accept(String adminMemo) {
        this.status = PostReportStatus.ACCEPTED;
        this.processedAt = LocalDateTime.now();
        this.adminMemo = adminMemo;
    }

    public void reject(String adminMemo) {
        this.status = PostReportStatus.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.adminMemo = adminMemo;
    }

    public boolean isPending() {
        return this.status == PostReportStatus.PENDING;
    }
}