package com.theo.community_api.user;

import com.theo.community_api.auth.dto.IssuedTokens;
import com.theo.community_api.auth.service.AuthService;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.dto.*;
import com.theo.community_api.user.repository.UserRepository;
import com.theo.community_api.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthService authService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입에 성공하면 요청 정보로 회원을 저장하고 저장된 회원 ID를 반환한다")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest(
                "theo1234@gmail.com",
                "Password123!",
                "Password123!",
                "theo",
                null
        );

        given(passwordEncoder.encode("Password123!"))
                .willReturn("encodedPassword");

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    // user 객체의 id 필드에 1L 값을 저장하라.
                    ReflectionTestUtils.setField(user, "id", 1L);
                    return user;
                });

        // when
        Long result = userService.signup(request);

        // then
        assertThat(result).isEqualTo(1L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());
        // userRepository.save() 가 호출됐을 때 전달된 User 객체를 가져오기
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("theo1234@gmail.com");
        assertThat(savedUser.getNickname()).isEqualTo("theo");
        assertThat(savedUser.getPassword()).isEqualTo("encodedPassword");
    }

    @Test
    @DisplayName("비밀번호와 비밀번호 확인이 다르면 회원가입에 실패한다")
    void signup_fail_when_password_mismatch() {
        // given
        SignupRequest request = new SignupRequest(
                "theo1234@gmail.com",
                "Password123!",
                "Different123!",
                "theo",
                null
        );

        // when : 회원가입 시도
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.signup(request) // when
        );

        // then : 비밀번호 불일치 에러 발생 및 검증
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verify(userRepository, never()) // 회원가입 함수 호출되어서는 안된다.
                .save(any(User.class));

        verifyNoInteractions(passwordEncoder); // 비밀번호 암호화 실행되서는 안된다.
    }

    @Test
    @DisplayName("이메일이 중복되면 회원가입에 실패한다")
    void signup_fail_when_email_duplicate() {
        // given
        SignupRequest request = new SignupRequest(
                "duplicate@gmail.com",
                "Password123!",
                "Password123!",
                "theo",
                null
        );

        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.signup(request)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXIST);

        verify(userRepository, never()) // 회원가입 함수 호출되어서는 안된다.
                .save(any(User.class));

        verifyNoInteractions(passwordEncoder); // 비밀번호 암호화 실행되서는 안된다.
    }

    @Test
    @DisplayName("닉네임이 중복되면 회원가입에 실패한다")
    void signup_fail_when_nickname_duplicate() {
        // given
        SignupRequest request = new SignupRequest(
                "theo1234@gmail.com",
                "Password123!",
                "Password123!",
                "duplicateNickname",
                null
        );

        given(userRepository.existsByEmail(request.getEmail()))
                .willReturn(false);

        given(userRepository.existsByNickname(request.getNickname()))
                .willReturn(true);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.signup(request)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXIST);

        verify(userRepository, never()) // 회원가입 함수 호출되어서는 안된다.
                .save(any(User.class));

        verifyNoInteractions(passwordEncoder); // 비밀번호 암호화 실행되서는 안된다.
    }

    @Test
    @DisplayName("회원가입 시 비밀번호는 암호화되어 저장된다")
    void signup_encrypts_password() {
        // given
        SignupRequest request = new SignupRequest(
                "theo1234@gmail.com",
                "Password123!",
                "Password123!",
                "theo",
                null
        );

        String encodedPassword = "encoded-password";

        given(passwordEncoder.encode(request.getPassword()))
                .willReturn(encodedPassword);

        given(userRepository.save(any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when
        userService.signup(request);

        // then
        verify(passwordEncoder).encode(request.getPassword());

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPassword())
                .isEqualTo(encodedPassword);
    }

    @Test
    @DisplayName("존재하는 회원을 조회하면 회원 정보를 반환한다")
    void getUser_success() {
        // given
        Long userId = 1L;

        User user = new User(
                "theo1234@gmail.com",
                "encoded-password",
                "theo",
                "profile.png"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getUser(userId);

        // then
        assertThat(response.getEmail())
                .isEqualTo("theo1234@gmail.com");

        assertThat(response.getNickname())
                .isEqualTo("theo");

        assertThat(response.getProfileImage())
                .isEqualTo("profile.png");
    }

    @Test
    @DisplayName("탈퇴한 회원을 조회하면 USER_NOT_FOUND가 발생한다")
    void getUser_fail_when_user_deleted() {
        // given
        Long userId = 1L;

        User deletedUser = createDeletedUser();

        deletedUser.delete();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(deletedUser));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.getUser(userId)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("회원정보를 정상적으로 수정한다")
    void updateUser_success() {
        // given
        Long userId = 1L;

        User user = new User(
                "theo1234@gmail.com",
                "encoded-password",
                "theo",
                "old-profile.png"
        );

        UserUpdateRequest request = new UserUpdateRequest(
                "newTheo",
                "new-profile.png"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(userRepository.existsByNickname(request.getNickname()))
                .willReturn(false);

        // when
        UserUpdateResponse response =
                userService.updateUser(userId, request);

        // then
        assertThat(response.getNickname())
                .isEqualTo("newTheo");

        assertThat(response.getProfileImage())
                .isEqualTo("new-profile.png");
    }

    @Test
    @DisplayName("현재 닉네임을 유지하면서 프로필 이미지만 수정한다")
    void updateUser_success_when_only_profile_image_changes() {
        // given
        Long userId = 1L;

        User user = new User(
                "theo1234@gmail.com",
                "encoded-password",
                "theo",
                null
        );

        UserUpdateRequest request = new UserUpdateRequest(
                "theo",
                "new-profile.png"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        // when
        UserUpdateResponse response = userService.updateUser(userId, request);

        // then
        assertThat(response.getNickname()).isEqualTo("theo");
        assertThat(response.getProfileImage()).isEqualTo("new-profile.png");

        verify(userRepository, never())
                .existsByNickname(any());
    }

    @Test
    @DisplayName("다른 회원이 사용하는 닉네임이면 NICKNAME_ALREADY_EXIST가 발생한다")
    void updateUser_fail_when_nickname_duplicate() {
        // given
        Long userId = 1L;

        User user = new User(
                "theo1234@gmail.com",
                "encoded-password",
                "theo",
                null
        );

        UserUpdateRequest request = new UserUpdateRequest(
                "existingNickname",
                "new-profile.png"
        );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(userRepository.existsByNickname(request.getNickname()))
                .willReturn(true);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUser(userId, request)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.NICKNAME_ALREADY_EXIST);
    }

    @Test
    @DisplayName("탈퇴한 회원의 정보 수정을 요청 시 UNAUTHORIZED_REQUEST가 발생한다")
    void updateUser_fail_when_unauthorized() {
        // given
        Long userId = 1L;

        User deletedUser = createDeletedUser();

        given(userRepository.findById(userId))
                .willReturn(Optional.of(deletedUser));

        deletedUser.delete();

        // when
        UserUpdateRequest request = new UserUpdateRequest(
                "newTheo",
                "new-profile.png"
        );


        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updateUser(userId, request)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED_REQUEST);

        verify(userRepository, never()) // 탈퇴한 회원이면 닉네임 중복검사가 호출되면 안된다
                .existsByNickname(any());

        assertThat(deletedUser.getNickname()) // 탈퇴한 회원의 닉네임은 "알 수 없음"으로 처리되야 한다.
                .isEqualTo("알 수 없음");

        assertThat(deletedUser.getProfileImage()) // 프로필 이미지는 null이 되어야 한다.
                .isNull();
    }

    @Test
    @DisplayName("비밀번호를 암호화하여 변경한다")
    void updatePassword_success() {
        // given
        Long userId = 1L;

        User user = createUser();

        PasswordUpdateRequest request =
                new PasswordUpdateRequest(
                        "NewPassword123!",
                        "NewPassword123!"
                );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )).willReturn(false);

        given(passwordEncoder.encode(request.getPassword()))
                .willReturn("encoded-new-password");

        // when
        userService.updatePassword(userId, request);

        // then
        assertThat(user.getPassword())
                .isEqualTo("encoded-new-password");
    }

    @Test
    @DisplayName("비밀번호 확인이 다르면 PASSWORD_MISMATCH가 발생한다")
    void updatePassword_fail_when_password_mismatch() {
        // given
        Long userId = 1L;

        PasswordUpdateRequest request =
                new PasswordUpdateRequest(
                        "NewPassword123!",
                        "DifferentPassword123!"
                );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updatePassword(userId, request)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("기존 비밀번호와 같으면 SAME_PASSWORD가 발생한다")
    void updatePassword_fail_when_same_password() {
        // given
        Long userId = 1L;

        User user = new User(
                "theo1234@gmail.com",
                "encoded-old-password", // 중복되는 비밀번호 작성
                "theo",
                null
        );

        PasswordUpdateRequest request =
                new PasswordUpdateRequest(
                        "OldPassword123!",
                        "OldPassword123!"
                );

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        given(passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )).willReturn(true);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.updatePassword(userId, request)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.SAME_PASSWORD);
    }

    @Test
    @DisplayName("회원 탈퇴에 성공하면 refreshToken을 제거하고 회원을 탈퇴 처리한다.")
    void deleteUser_success() {
        // given
        Long userId = 1L;

        User user = createUser();
        ReflectionTestUtils.setField(user, "id", userId);

        given(userRepository.findById(userId))
                .willReturn(Optional.of(user));

        // when
        userService.deleteUser(userId);

        // then
        verify(authService).revokeAllForUser(userId);

        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getEmail()).isEqualTo("deleted_user_" + userId + "@deleted.local");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getNickname()).isEqualTo("알 수 없음");
        assertThat(user.getProfileImage()).isNull();
    }

    @Test
    @DisplayName("이미 탈퇴한 회원을 다시 탈퇴하려 한다면 USER_NOT_FOUND 예외 발생")
    void deleteUser_fail_when_user_already_deleted(){
        // given
        Long userId = 1L;
        User deletedUser = createUser();
        ReflectionTestUtils.setField(deletedUser, "id", userId);
        deletedUser.delete();

        given(userRepository.findById(userId))
            .willReturn(Optional.of(deletedUser));

        // when
        BusinessException exception = assertThrows(BusinessException.class,
                () -> userService.deleteUser(userId)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("로그인에 성공하면 토큰을 발급한다")
    void login_success() {
        // given
        LoginRequest request = createLoginRequest();

        String currentRefreshToken = "old-refresh-token";

        User user = createUser();
        ReflectionTestUtils.setField(user, "id", 1L);

        IssuedTokens issuedTokens = new IssuedTokens(
                "access-token",
                "refresh-token",
                "Bearer",
                1800000L // 30분
        );

        given(userRepository.findByEmail(request.getEmail()))
                .willReturn(Optional.of(user));

        given(passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .willReturn(true);

        given(authService.createTokens(user, currentRefreshToken))
                .willReturn(issuedTokens);

        // when
        IssuedTokens result = userService.login(request, currentRefreshToken);

        // then
        assertThat(result).isEqualTo(issuedTokens);

        verify(authService).createTokens(user, currentRefreshToken);
    }

    @Test
    @DisplayName("이메일에 해당하는 회원이 없으면 로그인에 실패한다")
    void login_fail_when_email_not_found() {
        // given
        LoginRequest request = new LoginRequest(
                "unknown@gmail.com",
                "Password123!"
        );

        given(userRepository.findByEmail(request.getEmail()))
                .willReturn(Optional.empty());

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.login(request, null)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 로그인에 실패한다")
    void login_fail_when_password_mismatch() {
        // given
        LoginRequest request = new LoginRequest(
                "theo1234@gmail.com",
                "WrongPassword123!"
        );

        User user = createUser();

        given(userRepository.findByEmail(request.getEmail()))
                .willReturn(Optional.of(user));

        given(passwordEncoder.matches(request.getPassword(), user.getPassword()))
                .willReturn(false);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.login(request, null)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("탈퇴한 회원은 로그인할 수 없다")
    void login_fail_when_user_deleted() {
        // given
        LoginRequest request = createLoginRequest();

        User deletedUser = createUser();
        ReflectionTestUtils.setField(deletedUser, "id", 1L);
        deletedUser.delete();

        given(userRepository.findByEmail(request.getEmail()))
                .willReturn(Optional.of(deletedUser));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.login(request, null)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("비밀번호가 없는 회원은 로그인할 수 없다")
    void login_fail_when_password_is_null() {
        // given
        LoginRequest request = createLoginRequest();

        User user = new User(
                "theo1234@gmail.com",
                null,
                "theo",
                null
        );

        given(userRepository.findByEmail(request.getEmail()))
                .willReturn(Optional.of(user));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.login(request, null)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(authService);
    }

    private User createUser() {
        return new User(
                "theo1234@gmail.com",
                "encoded-password",
                "theo",
                null
        );
    }

    private User createDeletedUser() {
        User user = createUser();
        user.delete();
        return user;
    }

    private LoginRequest createLoginRequest() {
        return new LoginRequest(
                "theo1234@gmail.com",
                "Password123!"
        );
    }
}
