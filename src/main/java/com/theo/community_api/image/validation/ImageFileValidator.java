package com.theo.community_api.image.validation;

import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Component
public class ImageFileValidator {
    // 최대 이미지 크기 5MB
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    // 허용 이미지 형식
    private static final Map<String, String> EXTENSIONS =
            Map.of(
                    "image/jpeg", "jpg",
                    "image/png", "png",
                    "image/webp", "webp"
            );

    public ValidatedImage validate(MultipartFile file) {
        validateNotEmpty(file);
        validateFileSize(file);

        String contentType = file.getContentType();

        String extension = contentType == null ? null : EXTENSIONS.get(contentType);

        if (extension == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_IMAGE_TYPE
            );
        }

        byte[] content = readContent(file);

        if (!matchesFileSignature(contentType, content)) {
            throw new BusinessException(
                    ErrorCode.INVALID_IMAGE_TYPE
            );
        }

        return new ValidatedImage(
                content,
                contentType,
                extension
        );
    }

    private void validateNotEmpty(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.EMPTY_IMAGE_FILE
            );
        }
    }

    private void validateFileSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(
                    ErrorCode.IMAGE_FILE_TOO_LARGE
            );
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(
                    ErrorCode.IMAGE_READ_FAILED
            );
        }
    }

    private boolean matchesFileSignature(
            String contentType,
            byte[] content
    ) {
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(content);
            case "image/png" -> isPng(content);
            case "image/webp" -> isWebp(content);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] content) {
        return content.length >= 3
                && unsigned(content[0]) == 0xFF
                && unsigned(content[1]) == 0xD8
                && unsigned(content[2]) == 0xFF;
    }

    private boolean isPng(byte[] content) {
        int[] signature = {
                0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        if (content.length < signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if (unsigned(content[index]) != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R'
                && content[1] == 'I'
                && content[2] == 'F'
                && content[3] == 'F'
                && content[8] == 'W'
                && content[9] == 'E'
                && content[10] == 'B'
                && content[11] == 'P';
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    public record ValidatedImage(
            byte[] content,
            String contentType,
            String extension
    ) {
    }
}