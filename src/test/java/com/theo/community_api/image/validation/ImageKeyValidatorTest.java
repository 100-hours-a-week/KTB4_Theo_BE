package com.theo.community_api.image.validation;

import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.domain.ImageCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("이미지 객체 키 검증 테스트")
class ImageKeyValidatorTest {

    private ImageKeyValidator imageKeyValidator;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "test-bucket",
                "ap-northeast-2",
                "local",
                "community-api-local"
        );

        imageKeyValidator = new ImageKeyValidator(properties);
    }

    @Test
    @DisplayName("현재 사용자의 게시글 이미지 키는 검증에 성공한다")
    void validate_post_image_key_success() {
        // given
        String imageKey =
                "local/posts/15/550e8400-e29b-41d4-a716-446655440000.webp";

        // when & then
        assertDoesNotThrow(
                () -> imageKeyValidator.validateForUser(
                        imageKey,
                        ImageCategory.POST,
                        15L
                )
        );
    }

    @Test
    @DisplayName("현재 사용자의 프로필 이미지 키는 검증에 성공한다")
    void validate_profile_image_key_success() {
        // given
        String imageKey =
                "local/profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg";

        // when & then
        assertDoesNotThrow(
                () -> imageKeyValidator.validateForUser(
                        imageKey,
                        ImageCategory.PROFILE,
                        15L
                )
        );
    }

    @Test
    @DisplayName("현재 환경과 다른 이미지 키는 거부한다")
    void validate_fail_when_environment_is_different() {
        assertInvalidImageKey(
                "prod/posts/15/550e8400-e29b-41d4-a716-446655440000.jpg",
                ImageCategory.POST,
                15L
        );
    }

    @Test
    @DisplayName("다른 사용자의 이미지 키는 거부한다")
    void validate_fail_when_user_is_different() {
        assertInvalidImageKey(
                "local/posts/20/550e8400-e29b-41d4-a716-446655440000.jpg",
                ImageCategory.POST,
                15L
        );
    }

    @Test
    @DisplayName("요청한 카테고리와 다른 이미지 키는 거부한다")
    void validate_fail_when_category_is_different() {
        assertInvalidImageKey(
                "local/profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg",
                ImageCategory.POST,
                15L
        );
    }

    @Test
    @DisplayName("서버가 생성하지 않은 파일명의 이미지 키는 거부한다")
    void validate_fail_when_file_name_is_invalid() {
        assertInvalidImageKey(
                "local/posts/15/original-file-name.jpg",
                ImageCategory.POST,
                15L
        );
    }

    @Test
    @DisplayName("이미지 키 목록이 비어 있으면 검증을 생략한다")
    void validate_all_skip_when_image_keys_are_empty() {
        assertDoesNotThrow(
                () -> imageKeyValidator.validateAllForUser(
                        List.of(),
                        ImageCategory.POST,
                        15L
                )
        );
    }

    private void assertInvalidImageKey(
            String imageKey,
            ImageCategory category,
            Long userId
    ) {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageKeyValidator.validateForUser(
                        imageKey,
                        category,
                        userId
                )
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_KEY);
    }
}
