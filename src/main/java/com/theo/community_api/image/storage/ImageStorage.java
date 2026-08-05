package com.theo.community_api.image.storage;

import java.io.InputStream;
import java.util.List;

public interface ImageStorage {

    void upload(
            String objectKey,
            InputStream inputStream,
            long contentLength,
            String contentType
    );

    void delete(String objectKey);

    List<StoredImageObject> listObjects();
}
