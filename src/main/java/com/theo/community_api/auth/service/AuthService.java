package com.theo.community_api.auth.service;

import com.theo.community_api.auth.domain.RefreshToken;
import com.theo.community_api.auth.dto.IssuedTokens;
import com.theo.community_api.auth.jwt.JwtProperties;
import com.theo.community_api.auth.jwt.JwtTokenProvider;
import com.theo.community_api.auth.repository.RefreshTokenRepository;
import com.theo.community_api.auth.token.TokenHasher;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.common.time.UtcDateTimes;
import com.theo.community_api.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final TokenHasher tokenHasher;
    private final RefreshTokenRepository refreshTokenRepository;

    // JWT 토큰 발급
    @Transactional
    public IssuedTokens createTokens(User user, String currentRefreshToken) {
        // 같은 기기에서 재로그인한 경우 현재 기기의 기존 Refresh Token만 제거
        revoke(currentRefreshToken);
        return issueTokens(user);
    }

    private IssuedTokens issueTokens(User user) {
        String accessToken =
                jwtTokenProvider.createAccessToken(user.getId());

        String refreshToken =
                jwtTokenProvider.createRefreshToken(user.getId());

        String tokenHash =
                tokenHasher.hash(refreshToken);

        LocalDateTime expiresAt =
                UtcDateTimes.now().plus(
                        Duration.ofMillis(
                                jwtProperties.refreshTokenExpiration()
                        )
                );

        refreshTokenRepository.save(
                new RefreshToken(
                        user,
                        tokenHash,
                        expiresAt
                )
        );

        return new IssuedTokens(
                accessToken,
                refreshToken,
                "Bearer",
                jwtProperties.accessTokenExpiration()
        );
    }

    @Transactional
    public IssuedTokens reissue(String refreshToken) {
        Claims claims = parseRefreshToken(refreshToken);

        // Access Token을 재발급 용도로 사용하지 못하게 차단
        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        Long userId =
                jwtTokenProvider.getUserId(claims);

        String tokenHash =
                tokenHasher.hash(refreshToken);

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                                new BusinessException(
                                        ErrorCode.INVALID_REFRESH_TOKEN
                                )
                        );

        User user = storedToken.getUser();

        if (storedToken.isExpired() || user.isDeleted() || !user.getId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFRESH_TOKEN
            );
        }

        // 기존 Refresh Token 폐기
        refreshTokenRepository.delete(storedToken);

        // 새 Access/Refresh Token 생성 및 새 Refresh Token 저장
        return issueTokens(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        revoke(refreshToken);
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    @Transactional
    public void deleteExpiredRefreshTokens() {
        refreshTokenRepository.deleteAllByExpiresAtBefore(UtcDateTimes.now());
    }

    private void revoke(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.deleteByTokenHash(
                    tokenHasher.hash(refreshToken)
            );
        }
    }

    private Claims parseRefreshToken(String refreshToken) {
        try {
            return jwtTokenProvider.parseClaims(refreshToken);
        } catch (
                JwtException |
                IllegalArgumentException exception
        ) {
            throw new BusinessException(
                    ErrorCode.INVALID_REFRESH_TOKEN
            );
        }
    }
}
