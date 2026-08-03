package com.theo.community_api.user.service;

import com.theo.community_api.auth.dto.IssuedTokens;
import com.theo.community_api.auth.service.AuthService;
import com.theo.community_api.common.exception.*;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.dto.*;
import com.theo.community_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;

    // 회원 정보 조회
    public UserResponse getUser(Long loginUserId) {
        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        return UserResponse.from(user);
    }

    // 회원가입
    @Transactional
    public Long signup(SignupRequest request) {
        // 비밀번호, 재입력 비밀번호가 같은지 확인
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 이메일 중복확인
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXIST);
        }

        // 닉네임 중복확인
        if (userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXIST);
        }

        // 비밀번호 해시 처리: 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 사용자 추가
        User user = new User(
                request.getEmail(),
                encodedPassword,
                request.getNickname(),
                request.getProfileImage());

        User savedUser = userRepository.save(user);

        return savedUser.getId();
    }

    // 로그인
    @Transactional
    public IssuedTokens login(LoginRequest request, String currentRefreshToken) {
        // 이메일로 사용자 찾기
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 비밀번호와 DB 내 비밀번호(해시된 비밀번호)와 같은지 확인
        if (user.isDeleted() || user.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_CREDENTIALS
            );
        }

        return authService.createTokens(user, currentRefreshToken);
    }

    // 회원정보 수정
    @Transactional
    public UserUpdateResponse updateUser(Long loginUserId, UserUpdateRequest request) {
        // 유저 존재여부 확인
        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 삭제된 유저가 요청 시
        if(user.isDeleted()){
            throw new BusinessException(ErrorCode.UNAUTHORIZED_REQUEST);
        }

        boolean nicknameChanged = !user.getNickname().equals(request.getNickname());

        // 닉네임을 변경하는 경우에만 다른 사용자의 닉네임과 중복되는지 확인
        if (nicknameChanged && userRepository.existsByNickname(request.getNickname())) {
            throw new BusinessException(ErrorCode.NICKNAME_ALREADY_EXIST);
        }

        // 회원정보 갱신
        user.updateProfile(request.getNickname(), request.getProfileImage());

        return new UserUpdateResponse(user.getNickname(), user.getProfileImage());
    }

    // 비밀번호 수정
    @Transactional
    public void updatePassword(Long loginUserId, PasswordUpdateRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new BusinessException(ErrorCode.SAME_PASSWORD);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        user.updatePassword(encodedPassword);
    }

    // 회원 탈퇴
    @Transactional
    public void deleteUser(Long loginUserId) {
        User user = userRepository.findById(loginUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 이미 삭제된 유저라면
        if(user.isDeleted()){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 해당 회원 refreshToken DB에서 삭제
        authService.revokeAllForUser(user.getId());
        user.delete();
    }
}
