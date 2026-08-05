package com.theo.community_api.image.service;

import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.domain.ImageCategory;
import com.theo.community_api.image.validation.ImageFileValidator;
import com.theo.community_api.image.validation.ImageFileValidator.ValidatedImage;
import com.theo.community_api.image.storage.ImageKeyGenerator;
import com.theo.community_api.image.storage.ImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("이미지 서비스 테스트")
class ImageServiceTest {

    @Mock
    private ImageFileValidator imageFileValidator;

    @Mock
    private ImageKeyGenerator imageKeyGenerator;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private MultipartFile firstFile;

    @Mock
    private MultipartFile secondFile;

    @InjectMocks
    private ImageService imageService;

    @AfterEach
    void tearDownTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }

        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    @DisplayName("이미지를 S3에 업로드하고 생성된 객체 키를 반환한다")
    void upload_single_image_success() {
        // given
        Long userId = 15L;
        ImageCategory category = ImageCategory.POST;

        byte[] content = jpegContent();

        ValidatedImage validatedImage = validatedImage(
                content,
                "image/jpeg",
                "jpg"
        );

        String objectKey =
                "local/posts/15/test-image.jpg";

        given(imageFileValidator.validate(firstFile))
                .willReturn(validatedImage);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "jpg"
        )).willReturn(objectKey);

        // when
        List<String> result = imageService.upload(
                userId,
                category,
                List.of(firstFile)
        );

        // then
        assertThat(result)
                .containsExactly(objectKey);

        verify(imageStorage).upload(
                eq(objectKey),
                any(InputStream.class),
                eq((long) content.length),
                eq("image/jpeg")
        );
    }

    @Test
    @DisplayName("여러 이미지를 업로드하고 입력 순서대로 객체 키 목록을 반환한다")
    void upload_multiple_images_success() {
        // given
        Long userId = 15L;
        ImageCategory category = ImageCategory.POST;

        ValidatedImage jpegImage = validatedImage(
                jpegContent(),
                "image/jpeg",
                "jpg"
        );

        ValidatedImage pngImage = validatedImage(
                pngContent(),
                "image/png",
                "png"
        );

        String firstKey =
                "local/posts/15/first.jpg";

        String secondKey =
                "local/posts/15/second.png";

        given(imageFileValidator.validate(firstFile))
                .willReturn(jpegImage);

        given(imageFileValidator.validate(secondFile))
                .willReturn(pngImage);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "jpg"
        )).willReturn(firstKey);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "png"
        )).willReturn(secondKey);

        // when
        List<String> result = imageService.upload(
                userId,
                category,
                List.of(firstFile, secondFile)
        );

        // then
        assertThat(result)
                .containsExactly(firstKey, secondKey);

        verify(imageStorage, times(2)).upload(
                anyString(),
                any(InputStream.class),
                anyLong(),
                anyString()
        );
    }

    @Test
    @DisplayName("파일 목록이 null이면 EMPTY_IMAGE_FILE이 발생한다")
    void upload_fail_when_files_are_null() {
        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageService.upload(
                        15L,
                        ImageCategory.POST,
                        null
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_IMAGE_FILE);

        verifyNoInteractions(imageStorage);
    }

    @Test
    @DisplayName("파일 목록이 비어 있으면 EMPTY_IMAGE_FILE이 발생한다")
    void upload_fail_when_files_are_empty() {
        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageService.upload(
                        15L,
                        ImageCategory.POST,
                        List.of()
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.EMPTY_IMAGE_FILE);

        verifyNoInteractions(imageStorage);
    }

    @Test
    @DisplayName("파일 검증 중 오류가 발생하면 어떤 이미지도 S3에 업로드하지 않는다")
    void upload_fail_when_file_validation_fails() {
        // given
        ValidatedImage firstValidatedImage =
                validatedImage(
                        jpegContent(),
                        "image/jpeg",
                        "jpg"
                );

        BusinessException validationException =
                new BusinessException(
                        ErrorCode.INVALID_IMAGE_TYPE
                );

        given(imageFileValidator.validate(firstFile))
                .willReturn(firstValidatedImage);

        given(imageFileValidator.validate(secondFile))
                .willThrow(validationException);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageService.upload(
                        15L,
                        ImageCategory.POST,
                        List.of(firstFile, secondFile)
                )
        );

        // then
        assertThat(exception)
                .isSameAs(validationException);

        verifyNoInteractions(imageStorage);
    }

    @Test
    @DisplayName("일부 이미지 업로드가 실패하면 앞서 업로드한 객체를 삭제한다")
    void upload_fail_then_delete_previous_images() {
        // given
        Long userId = 15L;
        ImageCategory category = ImageCategory.POST;

        byte[] firstContent = jpegContent();
        byte[] secondContent = pngContent();

        ValidatedImage firstImage = validatedImage(
                firstContent,
                "image/jpeg",
                "jpg"
        );

        ValidatedImage secondImage = validatedImage(
                secondContent,
                "image/png",
                "png"
        );

        String firstKey =
                "local/posts/15/first.jpg";

        String secondKey =
                "local/posts/15/second.png";

        BusinessException uploadException =
                new BusinessException(
                        ErrorCode.IMAGE_UPLOAD_FAILED
                );

        given(imageFileValidator.validate(firstFile))
                .willReturn(firstImage);

        given(imageFileValidator.validate(secondFile))
                .willReturn(secondImage);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "jpg"
        )).willReturn(firstKey);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "png"
        )).willReturn(secondKey);

        // 첫 번째 업로드 호출은 정상적으로 처리되지만 두 번째 호출에서는 업로드 예외 발생
        doNothing()
                .doThrow(uploadException)
                .when(imageStorage)
                .upload(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageService.upload(
                        userId,
                        category,
                        List.of(firstFile, secondFile)
                )
        );

        // then
        assertThat(exception)
                .isSameAs(uploadException);

        verify(imageStorage).delete(firstKey);

        verify(imageStorage, never())
                .delete(secondKey);
    }

    @Test
    @DisplayName("롤백 삭제가 실패해도 원래 업로드 예외를 유지한다")
    void upload_fail_and_rollback_fail_then_keep_original_exception() {
        // given
        Long userId = 15L;
        ImageCategory category = ImageCategory.POST;

        byte[] firstContent = jpegContent();
        byte[] secondContent = pngContent();

        ValidatedImage firstImage = validatedImage(
                firstContent,
                "image/jpeg",
                "jpg"
        );

        ValidatedImage secondImage = validatedImage(
                secondContent,
                "image/png",
                "png"
        );

        String firstKey =
                "local/posts/15/first.jpg";

        String secondKey =
                "local/posts/15/second.png";

        BusinessException uploadException =
                new BusinessException(
                        ErrorCode.IMAGE_UPLOAD_FAILED
                );

        BusinessException rollbackException =
                new BusinessException(
                        ErrorCode.IMAGE_DELETE_FAILED
                );

        given(imageFileValidator.validate(firstFile))
                .willReturn(firstImage);

        given(imageFileValidator.validate(secondFile))
                .willReturn(secondImage);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "jpg"
        )).willReturn(firstKey);

        given(imageKeyGenerator.generate(
                category,
                userId,
                "png"
        )).willReturn(secondKey);

        doNothing()
                .doThrow(uploadException)
                .when(imageStorage)
                .upload(
                        anyString(),
                        any(InputStream.class),
                        anyLong(),
                        anyString()
                );

        doThrow(rollbackException)
                .when(imageStorage)
                .delete(firstKey);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> imageService.upload(
                        userId,
                        category,
                        List.of(firstFile, secondFile)
                )
        );

        // then
        assertThat(exception)
                .isSameAs(uploadException);
    }

    @Test
    @DisplayName("트랜잭션이 커밋된 이후 S3 이미지를 삭제한다")
    void delete_images_after_transaction_commit() {
        // given
        String imageKey =
                "local/profiles/15/550e8400-e29b-41d4-a716-446655440000.jpg";

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        // when
        imageService.deleteAfterCommit(List.of(imageKey));

        // then
        verify(imageStorage, never()).delete(imageKey);

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(imageStorage).delete(imageKey);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 새로 업로드한 S3 이미지를 삭제한다")
    void delete_uploaded_images_after_transaction_rollback() {
        // given
        String imageKey =
                "local/posts/15/550e8400-e29b-41d4-a716-446655440000.png";

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();

        // when
        imageService.deleteOnRollback(List.of(imageKey));

        // then
        verify(imageStorage, never()).delete(imageKey);

        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(
                    TransactionSynchronization.STATUS_ROLLED_BACK
            );
        }

        verify(imageStorage).delete(imageKey);
    }

    @Test
    @DisplayName("트랜잭션이 없으면 S3 이미지를 즉시 삭제한다")
    void delete_images_immediately_without_transaction() {
        // given
        String imageKey =
                "local/posts/15/550e8400-e29b-41d4-a716-446655440000.webp";

        // when
        imageService.deleteAfterCommit(List.of(imageKey));

        // then
        verify(imageStorage).delete(imageKey);
    }

    @Test
    @DisplayName("중복된 이미지 키는 한 번만 삭제한다")
    void delete_duplicate_image_key_once() {
        // given
        String imageKey =
                "local/posts/15/550e8400-e29b-41d4-a716-446655440000.png";

        // when
        imageService.deleteAfterCommit(
                List.of(imageKey, imageKey)
        );

        // then
        verify(imageStorage).delete(imageKey);
    }

    private ValidatedImage validatedImage(
            byte[] content,
            String contentType,
            String extension
    ) {
        return new ValidatedImage(
                content,
                contentType,
                extension
        );
    }

    private byte[] jpegContent() {
        return new byte[]{
                (byte) 0xFF,
                (byte) 0xD8,
                (byte) 0xFF
        };
    }

    private byte[] pngContent() {
        return new byte[]{
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };
    }
}
