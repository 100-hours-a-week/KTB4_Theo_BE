package com.theo.community_api.notification.sse;

import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseEmitterService {

    private static final long DEFAULT_TIMEOUT = Duration.ofHours(1).toMillis();
    // 클라이언트와 SSE 연결이 처음 생성되었을 때 사용되는 이벤트 이름
    private static final String CONNECT_EVENT_NAME = "connect";
    // 실제 알림 데이터를 전송할 때 사용하는 SSE 이벤트 이름
    private static final String NOTIFICATION_EVENT_NAME = "notification";

    private final SseEmitterRepository emitterRepository;
    private final SseMetrics sseMetrics;

    // SSE 구독 요청 처리
    public SseEmitter subscribe(Long userId, String clientId) {
        validateClientId(clientId);

        // 최대 1시간 동안 유지되는 SSE 연결 객체 생성
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT);
        // 연결 완료, 타임아웃, 오류 발생 시 저장소에 남아있는 emitter 제거하기 위한 콜백 등록
        registerCallbacks(userId, clientId, emitter);
        // 기존 emitter가 존재하는 경우 complete()를 호출해서 이전 요청을 대체한다.
        emitterRepository.replace(userId, clientId, emitter)
                .ifPresent(SseEmitter::complete);
        // 클라이언트에게 연결 확인용 이벤트를 즉시 전송
        sendConnectEvent(userId, clientId, emitter);

        return emitter;
    }

    // 접속 중인 사용자에게 알림을 전송
    public void sendNotification(
            Long userId,
            NotificationResponse notification
    ) {
        Map<String, SseEmitter> userEmitters =
                emitterRepository.findByUserId(userId);

        for (
                Map.Entry<String, SseEmitter> entry
                : userEmitters.entrySet()
        ) {
            sendToClient(
                    userId,
                    entry.getKey(),
                    entry.getValue(),
                    notification
            );
        }
    }

    // SSE emitter의 생명주기 콜백을 등록
    private void registerCallbacks(
            Long userId,
            String clientId,
            SseEmitter emitter
    ) {
        // SSE 연결이 정상적으로 종료되었을 때
        emitter.onCompletion(() -> deleteEmitter(userId, clientId, emitter));
        // 설정한 최대 연결 시간인 1시간이 지나면 실행 (연결완료 상태로 처리)
        emitter.onTimeout(() -> {
            deleteEmitter(userId, clientId, emitter);
            emitter.complete();
        });
        // SSE 연결 도중 오류가 발생하면 실행
        emitter.onError(
                exception -> deleteEmitter(userId, clientId, emitter)
        );
    }

    // SSE 연결 직후에 클라이언트에게 연결 확인 이벤트를 전송한다.
    private void sendConnectEvent(
            Long userId,
            String clientId,
            SseEmitter emitter
    ) {
        try {
            // SSE 이벤트를 생성해서 클라이언트에게 전송
            emitter.send(
                    SseEmitter.event()
                            .name(CONNECT_EVENT_NAME)
                            .data("connected")
            );
            sseMetrics.recordConnection();
        } catch (IOException | IllegalStateException exception) {
            // 초기 연결 이벤트 전송 실패 시 해당 emitter 정상적 사용 불가하다고 판단 후 제거 및 오류상태 반환
            deleteEmitter(userId, clientId, emitter);
            emitter.completeWithError(exception);
            sseMetrics.recordConnectionFailure();

            log.debug(
                    "SSE 초기 연결 이벤트 전송 실패. userId={}, clientId={}",
                    userId,
                    clientId,
                    exception
            );
        }
    }

    // 조회된 SSE emitter를 통해 실제 알림 이벤트를 전송
    private void sendToClient(
            Long userId,
            String clientId,
            SseEmitter emitter,
            NotificationResponse notification
    ) {
        try {
            // 실제 알림 이벤트 만들어서 클라이언트에게 JSON 형태로 전송
            emitter.send(
                    SseEmitter.event()
                            .id(notification.getNotificationId().toString())
                            .name(NOTIFICATION_EVENT_NAME)
                            .data(notification)
            );
            sseMetrics.recordNotificationSent();
        } catch (IOException | IllegalStateException exception) {
            // 전송 실패 시 해당 emitter 정상적이지 않다고 간주한 후 제거
            deleteEmitter(userId, clientId, emitter);
            sseMetrics.recordNotificationFailure();

            log.debug(
                    "SSE 알림 전송 실패. userId={}, clientId={}, notificationId={}",
                    userId,
                    clientId,
                    notification.getNotificationId(),
                    exception
            );
        }
    }

    // 저장소에서 특정 사용자의 SSE emitter 제거
    private void deleteEmitter(
            Long userId,
            String clientId,
            SseEmitter emitter
    ) {
        emitterRepository.delete(userId, clientId, emitter);
    }

    // clientId UUID 형식인지 확인
    private void validateClientId(String clientId){
        if (clientId == null || clientId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        try {
            UUID.fromString(clientId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
