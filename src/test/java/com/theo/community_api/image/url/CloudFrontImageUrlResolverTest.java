package com.theo.community_api.image.url;

import com.theo.community_api.common.config.CloudFrontProperties;
import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.validation.ImageKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudFrontImageUrlResolverTest {

    private CloudFrontImageUrlResolver imageUrlResolver;

    @BeforeEach
    void setUp() {
        CloudFrontProperties cloudFrontProperties =
                new CloudFrontProperties(
                        "https://d2no1l4qcrswb6.cloudfront.net"
                );

        S3Properties s3Properties =
                new S3Properties(
                        "test-bucket",
                        "ap-northeast-2",
                        "local",
                        "community-api-local"
                );

        imageUrlResolver =
                new CloudFrontImageUrlResolver(
                        cloudFrontProperties,
                        new ImageKeyValidator(s3Properties)
                );
    }

    @Test
    @DisplayName("S3 객체 Key를 CloudFront 조회 URL로 변환한다")
    void resolve_success() {
        // given
        String imageKey =
                "local/posts/15/image-id.webp";

        // when
        String imageUrl =
                imageUrlResolver.resolve(imageKey);

        // then
        assertThat(imageUrl).isEqualTo(
                "https://d2no1l4qcrswb6.cloudfront.net"
                        + "/local/posts/15/image-id.webp"
        );
    }

    @Test
    @DisplayName("이미지 Key가 null이면 null을 반환한다")
    void resolve_return_null_when_key_is_null() {
        // when
        String imageUrl =
                imageUrlResolver.resolve(null);

        // then
        assertThat(imageUrl).isNull();
    }

    @Test
    @DisplayName("이미지 Key가 공백이면 null을 반환한다")
    void resolve_return_null_when_key_is_blank() {
        // when
        String imageUrl =
                imageUrlResolver.resolve(" ");

        // then
        assertThat(imageUrl).isNull();
    }

    @Test
    @DisplayName("현재 환경과 다른 prefix의 Key는 거부한다")
    void resolve_fail_when_prefix_is_invalid() {
        // given
        String imageKey =
                "prod/posts/15/image-id.webp";

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageUrlResolver.resolve(imageKey)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_KEY);
    }
}
