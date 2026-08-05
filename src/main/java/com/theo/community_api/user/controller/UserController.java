package com.theo.community_api.user.controller;

import com.theo.community_api.auth.security.CustomUserDetails;
import com.theo.community_api.common.ApiResponse;
import com.theo.community_api.user.dto.*;
import com.theo.community_api.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    // 회원 정보 가져오기
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ){
        UserResponse response = userService.getUser(userDetails.getUserId());

        return ResponseEntity
                .ok(ApiResponse.of("get_user_success", response));
    }

    // 회원가입
    @PostMapping(
            value = "/signup",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<Long>> signup(
            @Valid @RequestPart("request") SignupRequest request,
            @RequestPart("profileImage") MultipartFile profileImage
    ) {
        Long userId = userService.signup(request, profileImage);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.of("signup_success", userId));
    }

    // 회원정보 수정
    @PatchMapping(
            value = "/me",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<UserUpdateResponse>> updateUser(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestPart("request") UserUpdateRequest request,
            @RequestPart(value = "profileImage", required = false)
            MultipartFile profileImage
    ) {
        UserUpdateResponse response = userService.updateUser(
                userDetails.getUserId(),
                request,
                profileImage
        );

        return ResponseEntity
                .ok(ApiResponse.of("user_update_success", response));
    }

    // 비밀번호 수정
    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        userService.updatePassword(userDetails.getUserId(), request);



        return ResponseEntity
                .ok(ApiResponse.of("password_update_success"));
    }

    // 회원 탈퇴
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        userService.deleteUser(userDetails.getUserId());

        return ResponseEntity
                .ok(ApiResponse.of("user_delete_success"));
    }
}
