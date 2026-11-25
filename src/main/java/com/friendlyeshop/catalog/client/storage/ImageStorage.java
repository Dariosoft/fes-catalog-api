package com.friendlyeshop.catalog.client.storage;

import com.friendlyeshop.catalog.model.dto.in.ProductImageContentDetails;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    StoredImage store(UUID productId, UUID imageId, MultipartFile file);

    ProductImageContentDetails load(String objectKey);
}
