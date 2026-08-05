package com.theo.community_api.image.service;

import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.domain.ImageCategory;
import com.theo.community_api.image.validation.ImageFileValidator;
import com.theo.community_api.image.validation.ImageFileValidator.ValidatedImage;
import com.theo.community_api.image.storage.ImageKeyGenerator;
import com.theo.community_api.image.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageService {

    private final ImageFileValidator imageFileValidator;
    private final ImageKeyGenerator imageKeyGenerator;
    private final ImageStorage imageStorage;

    public List<String> upload(
            Long userId,
            ImageCategory category,
            List<MultipartFile> files
    ) {
        validateFileCount(category, files);

        // 모든 파일이 검증되면 그때 S3에 전체 이미지 저장
        List<ValidatedImage> validatedImages =
                files.stream()
                        .map(imageFileValidator::validate)
                        .toList();

        List<String> uploadedKeys = new ArrayList<>();

        try {
            for (ValidatedImage image : validatedImages) {
                String objectKey = imageKeyGenerator.generate(
                        category,
                        userId,
                        image.extension()
                );

                imageStorage.upload(
                        objectKey,
                        new ByteArrayInputStream(image.content()),
                        image.content().length,
                        image.contentType()
                );

                uploadedKeys.add(objectKey);
            }
        } catch (RuntimeException exception) {
            rollbackUploadedImages(uploadedKeys);
            throw exception;
        }

        return List.copyOf(uploadedKeys);
    }

    public void deleteAfterCommit(Collection<String> imageKeys) {
        List<String> keysToDelete = normalizeKeys(imageKeys);

        if (keysToDelete.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            deleteImages(keysToDelete);
                        }
                    }
            );
            return;
        }

        deleteImages(keysToDelete);
    }

    public void deleteOnRollback(Collection<String> imageKeys) {
        List<String> keysToDelete = normalizeKeys(imageKeys);

        if (keysToDelete.isEmpty()) {
            return;
        }

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                deleteImages(keysToDelete);
                            }
                        }
                    }
            );
        }
    }

    // 이미지에 대한 키 목록을 받아서 사용할 수 있는 값만 남도록 정리
    private List<String> normalizeKeys(Collection<String> imageKeys) {
        if (imageKeys == null) {
            return List.of();
        }

        return imageKeys.stream()
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
    }

    private void validateFileCount(
            ImageCategory category,
            List<MultipartFile> files
    ) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.EMPTY_IMAGE_FILE
            );
        }
    }

    private void rollbackUploadedImages(
            List<String> uploadedKeys
    ) {
        for (String objectKey : uploadedKeys) {
            try {
                imageStorage.delete(objectKey);
            } catch (RuntimeException rollbackException) {
                // 롤백 삭제 실패가 원래 업로드 예외를 덮어쓰지 않도록 로그만 남긴다.
                log.warn(
                        "Failed to rollback uploaded image. key={}",
                        objectKey,
                        rollbackException
                );
            }
        }
    }

    private void deleteImages(List<String> imageKeys) {
        for (String imageKey : imageKeys) {
            try {
                imageStorage.delete(imageKey);
            } catch (RuntimeException exception) {
                log.warn(
                        "Failed to delete image. key={}",
                        imageKey,
                        exception
                );
            }
        }
    }
}
