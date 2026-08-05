package com.theo.community_api.image.storage;

import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.image.domain.ImageCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("이미지 객체 키 생성 테스트")
class ImageKeyGeneratorTest {

    private ImageKeyGenerator imageKeyGenerator;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "test-bucket",
                "ap-northeast-2",
                "local",
                "community-api-local"
        );

        imageKeyGenerator = new ImageKeyGenerator(properties);
    }

    @Test
    @DisplayName("게시글 이미지 키에 환경, 이미지 종류, 사용자 ID와 확장자가 포함된다")
    void generate_post_image_key() {
        // when
        String imageKey = imageKeyGenerator.generate(
                ImageCategory.POST,
                15L,
                "jpg"
        );

        // then
        assertThat(imageKey)
                .startsWith("local/posts/15/")
                .endsWith(".jpg");
    }

    @Test
    @DisplayName("프로필 이미지 키는 profiles 경로에 생성된다")
    void generate_profile_image_key() {
        // when
        String imageKey = imageKeyGenerator.generate(
                ImageCategory.PROFILE,
                20L,
                "webp"
        );

        // then
        assertThat(imageKey)
                .startsWith("local/profiles/20/")
                .endsWith(".webp");
    }

    @Test
    @DisplayName("같은 조건으로 생성해도 UUID가 달라 서로 다른 키가 생성된다")
    void generate_unique_image_key() {
        // when
        String firstKey = imageKeyGenerator.generate(
                ImageCategory.POST,
                15L,
                "png"
        );

        String secondKey = imageKeyGenerator.generate(
                ImageCategory.POST,
                15L,
                "png"
        );

        // then
        assertThat(firstKey).isNotEqualTo(secondKey);
    }
}