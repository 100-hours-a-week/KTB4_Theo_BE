package com.theo.community_api.post.dto;

import com.theo.community_api.common.time.UtcDateTimeConverter;
import com.theo.community_api.post.domain.PostReport;
import lombok.Getter;

import java.time.Instant;

@Getter
public class PostReportResponse {

    private final Long reportId;
    private final Long postId;
    private final Long userId;
    private final String reason;
    private final String status;
    private final Instant reportedAt;
    private final Instant processedAt;
    private final String adminMemo;

    private PostReportResponse(
            Long reportId,
            Long postId,
            Long userId,
            String reason,
            String status,
            Instant reportedAt,
            Instant processedAt,
            String adminMemo
    ) {
        this.reportId = reportId;
        this.postId = postId;
        this.userId = userId;
        this.reason = reason;
        this.status = status;
        this.reportedAt = reportedAt;
        this.processedAt = processedAt;
        this.adminMemo = adminMemo;
    }

    public static PostReportResponse from(PostReport postReport) {
        return new PostReportResponse(
                postReport.getId(),
                postReport.getPost().getId(),
                postReport.getUser().getId(),
                postReport.getReason(),
                postReport.getStatus().name(),
                UtcDateTimeConverter.toInstant(postReport.getReportedAt()),
                UtcDateTimeConverter.toInstant(postReport.getProcessedAt()),
                postReport.getAdminMemo()
        );
    }
}
