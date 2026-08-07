package com.theo.community_api.notification.controller;

import com.theo.community_api.auth.security.CustomUserDetails;
import com.theo.community_api.common.ApiResponse;
import com.theo.community_api.notification.dto.NotificationListResponse;
import com.theo.community_api.notification.dto.NotificationUnreadCountResponse;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.notification.sse.SseEmitterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> readNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Long lastNotificationId,
            @RequestParam(defaultValue = "20") int size
    ){
        NotificationListResponse response = notificationService.readNotifications(
                userDetails.getUserId(),
                lastNotificationId,
                size
        );

        return ResponseEntity.ok(
                ApiResponse.of(
                        "notification_list_read_success",
                        response
                )
        );
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<NotificationUnreadCountResponse>> readUnreadNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ){
        NotificationUnreadCountResponse response = notificationService.readUnreadNotificationsCount(
                userDetails.getUserId()
        );

        return ResponseEntity.ok(
                ApiResponse.of(
                        "notification_unread_count_read_success",
                        response
                )
        );
    }

    // 게시글 단건 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> readNotification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId
    ){
        notificationService.readNotification(
                userDetails.getUserId(),
                notificationId
        );

        return ResponseEntity.ok(
                ApiResponse.of("notification_read_success")
        );
    }

    // 게시글 전체 읽음 처리
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAllNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        notificationService.readAllNotifications(
                userDetails.getUserId()
        );

        return ResponseEntity.ok(
                ApiResponse.of("notification_read_all_success")
        );
    }

    @GetMapping(
            value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestHeader("X-SSE-Client-Id") String clientId
    ){
        return sseEmitterService.subscribe(
                userDetails.getUserId(),
                clientId
        );
    }
}
