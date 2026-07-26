package com.theo.community_api.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class NotificationListResponse {
    private List<NotificationResponse> notifications;
    private boolean hasNext;
    private Long nextCursor;
}
