package com.theo.community_api.notification.repository;

import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationSourceType;
import com.theo.community_api.notification.domain.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    boolean existsByTypeAndActorIdAndSourceTypeAndSourceId(
            NotificationType type,
            Long actorId,
            NotificationSourceType sourceType,
            Long sourceId
    );

    @Query("""
        select n
        from Notification n
        join fetch n.actor
        where n.receiver.id = :receiverId
        order by n.id desc
    """)
    List<Notification> findFirstPage(
            @Param("receiverId") Long receiverId,
            Pageable pageable
    );

    @Query("""
        select n
        from Notification n
        join fetch n.actor
        where n.receiver.id = :receiverId
          and n.id < :lastNotificationId
        order by n.id desc
    """)
    List<Notification> findNextPage(
            @Param("receiverId") Long receiverId,
            @Param("lastNotificationId") Long lastNotificationId,
            Pageable pageable
    );

    long countByReceiverIdAndReadAtIsNull(Long receiverId);

    boolean existsByIdAndReceiverId(
            Long notificationId,
            Long receiverId
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update Notification n
        set n.readAt = CURRENT_TIMESTAMP
        where n.id = :notificationId
        and n.receiver.id = :receiverId
        and n.readAt is null
    """) // 단건 읽음 처리
    int markAsRead( // 0 : 이미 읽었거나 없거나 다른 사용자 알림, 1 : 이번 요청에서 읽음 처리
            @Param("receiverId") Long receiverId,
            @Param("notificationId") Long notificationId
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
        update Notification n
        set n.readAt = CURRENT_TIMESTAMP
        where n.receiver.id = :receiverId
          and n.readAt is null
    """)
    int markAllAsRead(
            @Param("receiverId") Long receiverId
    );
}
