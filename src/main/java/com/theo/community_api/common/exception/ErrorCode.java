package com.theo.community_api.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode { // 비즈니스 예외 모음
    // 400
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "invalid_request"),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "password_mismatch"),
    EMPTY_DRAFT_CONTENT(HttpStatus.BAD_REQUEST, "empty_draft_content"),
    DRAFT_PUBLISH_REQUIRED_TITLE_AND_CONTENT(HttpStatus.BAD_REQUEST, "draft_public_required_title_and_content"),

    // 401
    UNAUTHORIZED_REQUEST(HttpStatus.UNAUTHORIZED, "unauthorized_request"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "invalid_credentials"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_refresh_token"),
    ACCESS_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "access_token_required"),
    ACCESS_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "access_token_expired"),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "invalid_access_token"),

    // 403
    POST_BLINDED(HttpStatus.FORBIDDEN, "post_blinded"),
    POST_MODIFY_FORBIDDEN(HttpStatus.FORBIDDEN, "post_modify_forbidden"),
    POST_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "post_delete_forbidden"),
    COMMENT_UPDATE_FORBIDDEN(HttpStatus.FORBIDDEN, "comment_update_forbidden"),
    COMMENT_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "comment_delete_forbidden"),
    REPLY_MODIFY_FORBIDDEN(HttpStatus.FORBIDDEN, "reply_modify_forbidden"),
    REPLY_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "reply_delete_forbidden"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "access_denied"),

    // 404
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "user_not_found"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "post_not_found"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "comment_not_found"),
    REPLY_NOT_FOUND(HttpStatus.NOT_FOUND, "reply_not_found"),
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "draft_not_found"),
    POST_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "post_report_not_found"),
    ALREADY_PROCESSED_REPORT(HttpStatus.CONFLICT, "already_processed_report"),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "notification_not_found"),

    // 409
    EMAIL_ALREADY_EXIST(HttpStatus.CONFLICT, "email_already_exist"),
    NICKNAME_ALREADY_EXIST(HttpStatus.CONFLICT, "nickname_already_exist"),
    SAME_NICKNAME(HttpStatus.CONFLICT, "same_nickname"),
    SAME_PASSWORD(HttpStatus.CONFLICT, "same_password"),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "duplicate_report"),
    DRAFT_ALREADY_EXISTS(HttpStatus.CONFLICT, "draft_already_exists"),
    ALREADY_REPORTED_POST(HttpStatus.CONFLICT, "already_reported_post"),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "internal_server_error");

    private final HttpStatus status;
    private final String message;
}