package com.theo.community_api.image.storage;

import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.validation.ImageKeyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile({"local", "prod"})
@RequiredArgsConstructor
public class S3ImageStorage implements ImageStorage {

    private final S3Client s3Client;
    private final S3Properties properties;
    private final ImageKeyValidator imageKeyValidator;

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    ) {
        imageKeyValidator.validateEnvironment(objectKey);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .build();

        try {
            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            inputStream,
                            contentLength
                    )
            );
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(
                    ErrorCode.IMAGE_UPLOAD_FAILED
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        imageKeyValidator.validateEnvironment(objectKey);

        DeleteObjectRequest request =
                DeleteObjectRequest.builder()
                        .bucket(properties.bucket())
                        .key(objectKey)
                        .build();

        try {
            s3Client.deleteObject(request);
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(
                    ErrorCode.IMAGE_DELETE_FAILED
            );
        }
    }

    @Override
    public List<StoredImageObject> listObjects() {
        List<StoredImageObject> objects = new ArrayList<>();
        String continuationToken = null;

        try {
            do {
                ListObjectsV2Request.Builder requestBuilder =
                        ListObjectsV2Request.builder()
                                .bucket(properties.bucket())
                                .prefix(properties.prefix() + "/");

                if (continuationToken != null) {
                    requestBuilder.continuationToken(continuationToken);
                }

                ListObjectsV2Response response = s3Client.listObjectsV2(
                        requestBuilder.build()
                );

                response.contents().forEach(object -> objects.add(
                        new StoredImageObject(
                                object.key(),
                                object.lastModified(),
                                object.size()
                        )
                ));
                // S3 객체가 1000개를 초과해도 continuationToken 을 통해서 모든 페이지를 조회할 수 있도록 한다.
                continuationToken = response.nextContinuationToken();
            } while (continuationToken != null);
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(ErrorCode.IMAGE_READ_FAILED);
        }

        return List.copyOf(objects);
    }

}
