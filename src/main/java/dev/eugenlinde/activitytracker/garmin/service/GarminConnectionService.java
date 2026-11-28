package dev.eugenlinde.activitytracker.garmin.service;

import dev.eugenlinde.activitytracker.common.exception.GarminKeyExistsException;
import dev.eugenlinde.activitytracker.common.exception.GarminKeyNotFoundException;
import dev.eugenlinde.activitytracker.garmin.GarminConnectionRepository;
import dev.eugenlinde.activitytracker.garmin.dto.GarminStatusResponse;
import dev.eugenlinde.activitytracker.garmin.model.GarminConnection;
import dev.eugenlinde.activitytracker.garmin.model.GarminSyncStatus;
import dev.eugenlinde.activitytracker.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional
public class GarminConnectionService {
    private final GarminConnectionRepository garminConnectionRepository;
    private final UserRepository userRepository;

    public GarminConnectionService(
            GarminConnectionRepository garminConnectionRepository,
            UserRepository userRepository
    ) {
        this.garminConnectionRepository = garminConnectionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public GarminStatusResponse status(Long userId) {
        return garminConnectionRepository
                .findByUserId(userId)
                .map(GarminStatusResponse::from)
                .orElseGet(GarminStatusResponse::notConnected);
    }

    public GarminStatusResponse add(Long userId, String apiKey) {
        if (garminConnectionRepository.existsByUserId(userId)) {
            throw new GarminKeyExistsException();
        }
        GarminConnection connection = new GarminConnection();
        connection.setUser(userRepository.getReferenceById(userId));
        connection.setApiKey(apiKey.trim());
        return GarminStatusResponse.from(garminConnectionRepository.saveAndFlush(connection));
    }

    public void remove(Long userId) {
        GarminConnection connection = garminConnectionRepository
                .findByUserId(userId)
                .orElseThrow(GarminKeyNotFoundException::new);
        garminConnectionRepository.delete(connection);
    }

    public void recordSync(Long userId, GarminSyncStatus status, Instant at) {
        garminConnectionRepository.findByUserId(userId).ifPresent(connection -> {
            connection.setLastSyncStatus(status);
            connection.setLastSyncAt(at);
            garminConnectionRepository.save(connection);
        });
    }
}
