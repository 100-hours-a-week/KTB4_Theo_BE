package com.theo.community_api.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NotificationUnreadCountResponse {
    private long unreadCount;
}
