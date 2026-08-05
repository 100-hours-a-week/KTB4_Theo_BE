package com.theo.community_api.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
@Getter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private final UserRole role = UserRole.USER;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(nullable = true, length = 255)
    private String password;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Column(name = "profile_image_key", length = 500)
    private String profileImageKey;

    private LocalDateTime deletedAt;

    public User(String email, String password, String nickname, String profileImageKey) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImageKey = profileImageKey;
    }

    // 닉네임과 프로필 이미지 값 변경
    public void updateProfile(String nickname, String profileImageKey){
        this.nickname = nickname;
        this.profileImageKey = profileImageKey;
    }

    public void updateProfileImage(String profileImageKey) {
        this.profileImageKey = profileImageKey;
    }

    // 비밀번호 수정
    public void updatePassword(String password){
        this.password = password;
    }

    // 회원 탈퇴 시 활용 (서비스)
    public void delete(){
        this.email = "deleted_user_" + this.id + "@deleted.local";
        this.password = null;
        this.nickname = "알 수 없음";
        this.profileImageKey = null;
        this.deletedAt = LocalDateTime.now();
    }

    // 회원 탈퇴여부 확인
    public boolean isDeleted(){
        return deletedAt != null;
    }
}
