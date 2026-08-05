package com.theo.community_api.image.service;

import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.validation.ImageFileValidator;
import com.theo.community_api.image.validation.ImageFileValidator.ValidatedImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@DisplayName("이미지 파일 검증 테스트")
class ImageFileValidatorTest {

    private static final int FIVE_MB = 5 * 1024 * 1024; // 한 파일당 최대 5MB

    private ImageFileValidator imageFileValidator;

    @BeforeEach
    void setUp() {
        imageFileValidator = new ImageFileValidator();
    }

    @Test
    @DisplayName("JPEG 파일의 MIME 타입과 시그니처가 유효하면 검증에 성공한다")
    void validate_jpeg_success() {
        // given
        byte[] content = {
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF,
                0x00
        };

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.jpg",
                "image/jpeg",
                content
        );

        // when
        ValidatedImage result =
                imageFileValidator.validate(file);

        // then
        assertThat(result.contentType())
                .isEqualTo("image/jpeg");
        assertThat(result.extension())
                .isEqualTo("jpg");
        assertThat(result.content())
                .containsExactly(content);
    }

    @Test
    @DisplayName("PNG 파일의 MIME 타입과 시그니처가 유효하면 검증에 성공한다")
    void validate_png_success() {
        // given
        byte[] content = {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
                0x00
        };

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.png",
                "image/png",
                content
        );

        // when
        ValidatedImage result =
                imageFileValidator.validate(file);

        // then
        assertThat(result.contentType())
                .isEqualTo("image/png");
        assertThat(result.extension())
                .isEqualTo("png");
        assertThat(result.content())
                .containsExactly(content);
    }

    @Test
    @DisplayName("WebP 파일의 MIME 타입과 시그니처가 유효하면 검증에 성공한다")
    void validate_webp_success() {
        // given
        byte[] content = {
                'R', 'I', 'F', 'F',
                0x00, 0x00, 0x00, 0x00,
                'W', 'E', 'B', 'P'
        };

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.webp",
                "image/webp",
                content
        );

        // when
        ValidatedImage result =
                imageFileValidator.validate(file);

        // then
        assertThat(result.contentType())
                .isEqualTo("image/webp");
        assertThat(result.extension())
                .isEqualTo("webp");
        assertThat(result.content())
                .containsExactly(content);
    }

    @Test
    @DisplayName("파일이 null이면 EMPTY_IMAGE_FILE이 발생한다")
    void validate_fail_when_file_is_null() {
        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(null)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_IMAGE_FILE);
    }

    @Test
    @DisplayName("파일 내용이 비어 있으면 EMPTY_IMAGE_FILE이 발생한다")
    void validate_fail_when_file_is_empty() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_IMAGE_FILE);
    }

    @Test
    @DisplayName("파일 크기가 5MB를 초과하면 IMAGE_FILE_TOO_LARGE가 발생한다")
    void validate_fail_when_file_is_too_large() {
        // given
        byte[] content = new byte[FIVE_MB + 1];

        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                content
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("지원하지 않는 MIME 타입이면 INVALID_IMAGE_TYPE이 발생한다")
    void validate_fail_when_content_type_is_not_supported() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image.gif",
                "image/gif",
                new byte[]{0x01, 0x02, 0x03}
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    @DisplayName("MIME 타입이 없으면 INVALID_IMAGE_TYPE이 발생한다")
    void validate_fail_when_content_type_is_null() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "image",
                null,
                new byte[]{0x01, 0x02, 0x03}
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    @DisplayName("JPEG MIME 타입과 실제 파일 시그니처가 다르면 INVALID_IMAGE_TYPE이 발생한다")
    void validate_fail_when_jpeg_signature_is_invalid() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                new byte[]{0x01, 0x02, 0x03}
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    @DisplayName("PNG MIME 타입과 실제 파일 시그니처가 다르면 INVALID_IMAGE_TYPE이 발생한다")
    void validate_fail_when_png_signature_is_invalid() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.png",
                "image/png",
                new byte[]{
                        (byte) 0x89,
                        0x50,
                        0x4E
                }
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_TYPE);
    }

    @Test
    @DisplayName("파일 내용을 읽을 수 없으면 IMAGE_READ_FAILED가 발생한다")
    void validate_fail_when_file_cannot_be_read()
            throws IOException {
        // given
        MultipartFile file = mock(MultipartFile.class);

        givenValidFileMetadata(file);

        when(file.getBytes())
                .thenThrow(new IOException("read failed"));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageFileValidator.validate(file)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_READ_FAILED);
    }

    private void givenValidFileMetadata(
            MultipartFile file
    ) {
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType())
                .thenReturn("image/jpeg");
    }
}