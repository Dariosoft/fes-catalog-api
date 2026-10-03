package com.friendlyeshop.catalog.client.storage;

import com.friendlyeshop.catalog.model.dto.ProductImageContent;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    StoredImage store(UUID productId, UUID imageId, MultipartFile file);

    ProductImageContent load(String objectKey);
}
