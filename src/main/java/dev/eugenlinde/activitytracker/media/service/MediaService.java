package dev.eugenlinde.activitytracker.media.service;

import dev.eugenlinde.activitytracker.activity.ActivityRepository;
import dev.eugenlinde.activitytracker.activity.model.Activity;
import dev.eugenlinde.activitytracker.common.exception.ActivityNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.MediaNotFoundException;
import dev.eugenlinde.activitytracker.common.exception.StorageException;
import dev.eugenlinde.activitytracker.common.exception.UnsupportedMediaTypeException;
import dev.eugenlinde.activitytracker.media.MediaRepository;
import dev.eugenlinde.activitytracker.media.dto.MediaDownload;
import dev.eugenlinde.activitytracker.media.dto.MediaResponse;
import dev.eugenlinde.activitytracker.media.model.Media;
import dev.eugenlinde.activitytracker.media.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MediaService {
    private final MediaRepository mediaRepository;
    private final ActivityRepository activityRepository;
    private final StorageService storageService;

    public MediaService(
            MediaRepository mediaRepository,
            ActivityRepository activityRepository,
            StorageService storageService
    ) {
        this.mediaRepository = mediaRepository;
        this.activityRepository = activityRepository;
        this.storageService = storageService;
    }

    @Transactional()
    public MediaResponse uploadMedia(Long id, MultipartFile multipartFile, Long userId) {
        Activity activity = activityRepository.findByIdAndUserId(id, userId).orElseThrow(ActivityNotFoundException::new);

        try {
            String contentType = multipartFile.getContentType();

            if (contentType == null) {
                throw new UnsupportedMediaTypeException("Content type missing");
            }

            if (!contentType.equals("image/jpeg")) {
                throw new UnsupportedMediaTypeException("Only images can be uploaded");
            }

            String storageKey = this.storeFile(multipartFile);

            Media media = new Media();
            media.setActivity(activity);
            media.setStorageKey(storageKey);
            media.setContentType(multipartFile.getContentType());
            mediaRepository.save(media);

            return new MediaResponse(media.getId());
        } catch (IOException e) {
            throw new StorageException(e.getMessage());
        }
    }

    private String storeFile(MultipartFile multipartFile) throws IOException {
        InputStream inputStream = multipartFile.getInputStream();
        String storageKey;

        try {
            storageKey = storageService.store(inputStream);
        } finally {
            inputStream.close();
        }
        return storageKey;
    }

    @Transactional(readOnly = true)
    public MediaDownload getMedia(Long id) {
        Media media = mediaRepository.findById(id).orElseThrow(MediaNotFoundException::new);

        Resource resource = storageService.load(media.getStorageKey());
        MediaType mediaType = MediaType.parseMediaType(media.getContentType());
        return new MediaDownload(resource, mediaType);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasPermission(#activityId, 'Activity', 'READ')")
    public List<MediaResponse> findAllByActivityId(Long activityId) {
        List<Media> mediaList = mediaRepository.findAllByActivityId(activityId);

        return mediaList.stream().map(media -> new MediaResponse(media.getId())).collect(Collectors.toList());
    }

    @Transactional()
    @PreAuthorize("hasPermission(#id, 'Media', 'DELETE')")
    public void deleteById(Long id) {
        Media media = mediaRepository.findById(id).orElseThrow(MediaNotFoundException::new);
        String storageKey = media.getStorageKey();
        mediaRepository.delete(media);
        registerStorageDelete(storageKey);
    }

    private TransactionSynchronization buildStorageDeleteCallback(String storageKey) {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.delete(storageKey);
                } catch (StorageException e) {
                    log.error("Error deleting storage key", e);
                }
            }
        };
    }

    private void registerStorageDelete(String storageKey) {
        TransactionSynchronization transactionSynchronization = buildStorageDeleteCallback(storageKey);
        TransactionSynchronizationManager.registerSynchronization(transactionSynchronization);
    }

    @Transactional
    public void deleteAllForActivity(Long id) {
        List<Media> mediaList = mediaRepository.findAllByActivityId(id);
        mediaRepository.deleteAll(mediaList);

        for (Media media : mediaList) {
            String storageKey = media.getStorageKey();
            registerStorageDelete(storageKey);
        }
    }
}
