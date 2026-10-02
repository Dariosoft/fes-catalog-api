package com.friendlyeshop.catalog.client.storage;

import com.friendlyeshop.catalog.config.StorageProperties;
import com.friendlyeshop.catalog.exception.StorageUnavailableException;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MinioImageStorage implements ImageStorage {

    private static final String OBJECT_KEY_PATTERN = "products/%s/%s";

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final MinioClient minioClient;

    private final StorageProperties properties;

    public MinioImageStorage(MinioClient minioClient, StorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public StoredImage store(UUID productId, UUID imageId, MultipartFile file) {
        String objectKey = OBJECT_KEY_PATTERN.formatted(productId, imageId);
        String contentType = resolveContentType(file);
        try (InputStream input = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .contentType(contentType)
                    .stream(input, file.getSize(), -1)
                    .build());
        } catch (Exception exception) {
            throw new StorageUnavailableException("No se pudo almacenar la imagen del producto", exception);
        }
        return new StoredImage(objectKey, contentType);
    }

    @Override
    public ImageContent load(String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(objectKey)
                    .build());
            return new ImageContent(new InputStreamResource(stream), stat.contentType());
        } catch (Exception exception) {
            throw new StorageUnavailableException("No se pudo leer la imagen del producto", exception);
        }
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        return contentType;
    }
}
