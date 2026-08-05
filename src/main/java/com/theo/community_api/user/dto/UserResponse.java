package com.theo.community_api.user.dto;

import com.theo.community_api.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
    private String email;
    private String nickname;
    private String profileImageUrl;

    public static UserResponse from(User user, String profileImageUrl) {
        return new UserResponse(
                user.getEmail(),
                user.getNickname(),
                profileImageUrl
        );
    }
}
