package dev.eugenlinde.activitytracker.media.storage;

import dev.eugenlinde.activitytracker.common.exception.StorageException;
import dev.eugenlinde.activitytracker.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class LocalStorageService implements StorageService {

    private final Path normalizedPath;

    public LocalStorageService(StorageProperties storageProperties) {
        normalizedPath = storageProperties.getRootPath().toAbsolutePath().normalize();

        try {
            Files.createDirectories(normalizedPath);
        } catch (IOException e) {
            throw new StorageException("Unable to create storage directory", e);
        }
    }

    @Override
    public String store(InputStream content) {
        String storageKey = UUID.randomUUID().toString();
        Path target = normalizedPath.resolve(storageKey);

        try {
            Files.copy(content, target);
        } catch (IOException e) {
            throw new StorageException("Failed to store " + storageKey, e);
        }

        return storageKey;
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new StorageException("Unable to load: " + key);
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    private Path resolve(String key) {
        if (key == null || key.isBlank()) {
            throw new StorageException("Storage key missing: " + key);
        }
        Path file = normalizedPath.resolve(key).normalize();
        if (!normalizedPath.equals(file.getParent())) {
            throw new StorageException("Invalid storage key: " + key);
        }
        return file;
    }
}
