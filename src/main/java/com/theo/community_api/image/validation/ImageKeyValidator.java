package com.theo.community_api.image.validation;

import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.domain.ImageCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ImageKeyValidator {

    private static final Pattern GENERATED_FILE_NAME_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(jpg|png|webp)$"
    );

    private final S3Properties properties;

    public void validateEnvironment(String imageKey) {
        String allowedPrefix = properties.prefix() + "/";

        if (imageKey == null
                || imageKey.isBlank()
                || !imageKey.startsWith(allowedPrefix)) {
            throwInvalidImageKey();
        }
    }

    public void validateForUser(
            String imageKey,
            ImageCategory category,
            Long userId
    ) {
        validateEnvironment(imageKey);

        if (category == null || userId == null || userId < 1) {
            throwInvalidImageKey();
        }

        String expectedPrefix = "%s/%s/%d/".formatted(
                properties.prefix(),
                category.directory(),
                userId
        );

        if (!imageKey.startsWith(expectedPrefix)) {
            throwInvalidImageKey();
        }

        String fileName = imageKey.substring(expectedPrefix.length());

        if (!GENERATED_FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throwInvalidImageKey();
        }
    }

    public void validateAllForUser(
            List<String> imageKeys,
            ImageCategory category,
            Long userId
    ) {
        if (imageKeys == null || imageKeys.isEmpty()) {
            return;
        }

        Set<String> uniqueKeys = new HashSet<>();

        for (String imageKey : imageKeys) {
            validateForUser(imageKey, category, userId);

            if (!uniqueKeys.add(imageKey)) {
                throwInvalidImageKey();
            }
        }
    }

    private void throwInvalidImageKey() {
        throw new BusinessException(
                ErrorCode.INVALID_IMAGE_KEY
        );
    }
}
