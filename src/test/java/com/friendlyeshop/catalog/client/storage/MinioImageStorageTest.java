package com.friendlyeshop.catalog.client.storage;

import com.friendlyeshop.catalog.model.dto.ProductImageContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.config.StorageProperties;
import com.friendlyeshop.catalog.exception.StorageUnavailableException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MinioImageStorageTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private StatObjectResponse statObjectResponse;

    @Mock
    private GetObjectResponse getObjectResponse;

    private MinioImageStorage storage;

    private UUID productId;

    private UUID imageId;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "http://localhost:9000", "product-images", "key", "secret");
        storage = new MinioImageStorage(minioClient, properties);
        productId = UUID.randomUUID();
        imageId = UUID.randomUUID();
    }

    @Test
    void storesImageUnderProductKey() throws Exception {
        MockMultipartFile file = new MockMultipartFile("images", "photo.png", "image/png", "data".getBytes());

        StoredImage stored = storage.store(productId, imageId, file);

        assertThat(stored.objectKey()).isEqualTo("products/%s/%s".formatted(productId, imageId));
        assertThat(stored.contentType()).isEqualTo("image/png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void translatesStoreFailure() throws Exception {
        MockMultipartFile file = new MockMultipartFile("images", "photo.png", "image/png", "data".getBytes());
        doThrow(new RuntimeException("minio down")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatThrownBy(() -> storage.store(productId, imageId, file))
                .isInstanceOf(StorageUnavailableException.class);
    }

    @Test
    void loadsImageWithContentType() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(statObjectResponse);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(getObjectResponse);
        when(statObjectResponse.contentType()).thenReturn("image/jpeg");

        ProductImageContent content = storage.load("products/a/b");

        assertThat(content.contentType()).isEqualTo("image/jpeg");
        assertThat(content.resource()).isNotNull();
    }

    @Test
    void translatesLoadFailure() throws Exception {
        doThrow(new RuntimeException("minio down")).when(minioClient).statObject(any(StatObjectArgs.class));

        assertThatThrownBy(() -> storage.load("products/a/b")).isInstanceOf(StorageUnavailableException.class);
    }
}
