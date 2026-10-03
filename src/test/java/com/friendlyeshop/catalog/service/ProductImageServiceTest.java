package com.friendlyeshop.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.friendlyeshop.catalog.client.storage.ImageContent;
import com.friendlyeshop.catalog.client.storage.ImageStorage;
import com.friendlyeshop.catalog.exception.ProductNotFoundException;
import com.friendlyeshop.catalog.model.ProductImage;
import com.friendlyeshop.catalog.repository.ProductImageRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ImageStorage imageStorage;

    @InjectMocks
    private ProductImageService productImageService;

    @Test
    void readsTheStoredImageContent() {
        UUID imageId = UUID.randomUUID();
        ProductImage image = ProductImage.of(imageId, UUID.randomUUID(), "products/a/b", "image/png");
        ImageContent expected = new ImageContent(new ByteArrayResource("bytes".getBytes()), "image/png");
        when(productImageRepository.findById(imageId)).thenReturn(Optional.of(image));
        when(imageStorage.load("products/a/b")).thenReturn(expected);

        assertThat(productImageService.read(imageId)).isSameAs(expected);
    }

    @Test
    void failsWhenTheImageDoesNotExist() {
        UUID imageId = UUID.randomUUID();
        when(productImageRepository.findById(imageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productImageService.read(imageId))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
