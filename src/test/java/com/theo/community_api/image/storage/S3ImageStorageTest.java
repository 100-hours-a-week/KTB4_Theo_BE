package com.theo.community_api.image.storage;

import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.validation.ImageKeyValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3 이미지 저장소 테스트")
class S3ImageStorageTest {

    @Mock
    private S3Client s3Client;

    private S3ImageStorage imageStorage;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties(
                "test-bucket",
                "ap-northeast-2",
                "local",
                "community-api-local"
        );

        imageStorage = new S3ImageStorage(
                s3Client,
                properties,
                new ImageKeyValidator(properties)
        );
    }

    @Test
    @DisplayName("이미지를 설정된 버킷과 객체 키로 S3에 업로드한다")
    void upload_success() {
        // given
        String objectKey = "local/posts/15/test.jpg";
        byte[] content = "test-image".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);

        // when
        imageStorage.upload(
                objectKey,
                inputStream,
                content.length,
                "image/jpeg"
        );

        // then
        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                requestCaptor.capture(),
                any(RequestBody.class)
        );

        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(objectKey);
        assertThat(request.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("이미지 객체 키를 사용하여 S3에서 이미지를 삭제한다")
    void delete_success() {
        // given
        String objectKey = "local/posts/15/test.jpg";

        // when
        imageStorage.delete(objectKey);

        // then
        ArgumentCaptor<DeleteObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(requestCaptor.capture());

        DeleteObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.key()).isEqualTo(objectKey);
    }

    @Test
    @DisplayName("현재 환경과 다른 prefix의 객체 키는 업로드할 수 없다")
    void upload_fail_when_environment_prefix_is_different() {
        // given
        String objectKey = "prod/posts/15/test.jpg";
        byte[] content = "test-image".getBytes(StandardCharsets.UTF_8);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageStorage.upload(
                        objectKey,
                        new ByteArrayInputStream(content),
                        content.length,
                        "image/jpeg"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_KEY);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("현재 환경과 다른 prefix의 객체 키는 삭제할 수 없다")
    void delete_fail_when_environment_prefix_is_different() {
        // given
        String objectKey = "prod/posts/15/test.jpg";

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageStorage.delete(objectKey)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_KEY);

        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("S3 업로드 중 오류가 발생하면 IMAGE_UPLOAD_FAILED로 변환한다")
    void upload_fail_when_s3_throws_exception() {
        // given
        String objectKey = "local/posts/15/test.jpg";
        byte[] content = "test-image".getBytes(StandardCharsets.UTF_8);

        given(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).willThrow(
                S3Exception.builder()
                        .message("S3 upload failed")
                        .statusCode(500)
                        .build()
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageStorage.upload(
                        objectKey,
                        new ByteArrayInputStream(content),
                        content.length,
                        "image/jpeg"
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_UPLOAD_FAILED);
    }

    @Test
    @DisplayName("S3 삭제 중 오류가 발생하면 IMAGE_DELETE_FAILED로 변환한다")
    void delete_fail_when_s3_throws_exception() {
        // given
        String objectKey = "local/posts/15/test.jpg";

        given(s3Client.deleteObject(
                any(DeleteObjectRequest.class)
        )).willThrow(
                S3Exception.builder()
                        .message("S3 delete failed")
                        .statusCode(500)
                        .build()
        );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageStorage.delete(objectKey)
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.IMAGE_DELETE_FAILED);
    }
}
