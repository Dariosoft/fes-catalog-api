package com.friendlyeshop.catalog.client.storage;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorage {

    StoredImage store(UUID productId, UUID imageId, MultipartFile file);

    ImageContent load(String objectKey);
}
