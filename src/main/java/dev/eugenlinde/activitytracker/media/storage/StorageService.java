package dev.eugenlinde.activitytracker.media.storage;

import org.springframework.core.io.Resource;

import java.io.InputStream;

public interface StorageService {

    String store(InputStream content);

    Resource load(String storageKey);

    void delete(String storageKey);
}
