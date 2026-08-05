package com.theo.community_api.user.dto;

import com.theo.community_api.common.ValidationConst;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {
    @NotBlank(message = "invalid_request")
    @Email(message = "invalid_email_format")
    private String email;

    @NotBlank(message = "invalid_request")
    @Pattern( // 비밀번호 8자 이상, 20자 이하, 대문자, 소문자, 특수문자 각각 최소 1개 포함
            regexp = ValidationConst.PASSWORD_REGEX,
            message = "invalid_password_format"
    )
    private String password;

    @NotBlank(message = "invalid_request")
    private String passwordConfirm;

    @NotBlank(message = "invalid_request")
    @Size(max = 10, message = "invalid_nickname_format")
    @Pattern( // 띄어쓰기 불가
            regexp = ValidationConst.NICKNAME_REGEX,
            message = "invalid_nickname_format"
    )
    private String nickname;

    public SignupRequest(
            String email,
            String password,
            String passwordConfirm,
            String nickname
    ) {
        this.email = email;
        this.password = password;
        this.passwordConfirm = passwordConfirm;
        this.nickname = nickname;
    }
}
