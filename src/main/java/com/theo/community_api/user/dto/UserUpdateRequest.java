package com.theo.community_api.user.dto;

import com.theo.community_api.common.ValidationConst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequest {
    @NotBlank(message = "blank_nickname")
    @Size(max = 10, message = "invalid_nickname_format")
    @Pattern(regexp = ValidationConst.NICKNAME_REGEX, message = "invalid_nickname_format")
    private String nickname;

    public UserUpdateRequest(String nickname) {
        this.nickname = nickname;
    }
}
