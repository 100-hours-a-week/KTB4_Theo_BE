package com.theo.community_api.common.exception;

import com.theo.community_api.common.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    // DTO의 @Valid 검증 실패를 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();

        String message = fieldError != null ? fieldError.getDefaultMessage() : "invalid_request";

        return ResponseEntity
                .badRequest()
                .body(ApiResponse.of(message));
    }

    // JSON 파싱 실패, 타입 불일치 관리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleHttpMessageNotReadable(HttpMessageNotReadableException e){
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.of(ErrorCode.INVALID_REQUEST.getMessage()));
    }

    // 예상되는 예외처리 (service 코드에서 작성해준 예외)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.of(e.getMessage()));
    }

    // HTTP 파라미터 타입 오류 처리
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e
    ) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.of(
                        ErrorCode.INVALID_REQUEST.getMessage()
                ));
    }

    // 필수 요청 파라미터 또는 multipart 파일 누락
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiResponse<?>> handleMissingRequestValue(Exception e) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.of(ErrorCode.INVALID_REQUEST.getMessage()));
    }

    // multipart 파일 또는 전체 요청 크기 초과
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e
    ) {
        return ResponseEntity
                .status(ErrorCode.IMAGE_FILE_TOO_LARGE.getStatus())
                .body(ApiResponse.of(ErrorCode.IMAGE_FILE_TOO_LARGE.getMessage()));
    }

    // 파싱할 수 없는 multipart 요청
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<?>> handleMultipartException(MultipartException e) {
        return ResponseEntity
                .status(ErrorCode.INVALID_REQUEST.getStatus())
                .body(ApiResponse.of(ErrorCode.INVALID_REQUEST.getMessage()));
    }

    // 엔드포인트가 지원하지 않는 요청 Content-Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e
    ) {
        return ResponseEntity
                .status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ApiResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage()));
    }

    // 예상 못한 예외는 500으로 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("Unhandled exception", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.of("internal_server_error"));
    }

    @ExceptionHandler({
            AsyncRequestTimeoutException.class,
            AsyncRequestNotUsableException.class
    })
    public void handleAsyncRequestException(Exception exception) {
        // SSE 연결 종료 과정에서 발생할 수 있으므로
        // 별도의 JSON 응답을 작성하지 않는다.
    }
}
