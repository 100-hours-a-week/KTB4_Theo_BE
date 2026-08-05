package com.theo.community_api.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserUpdateResponse {
    private String nickname;
    private String profileImageUrl;
}
