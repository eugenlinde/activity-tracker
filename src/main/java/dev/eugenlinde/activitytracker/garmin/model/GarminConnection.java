package dev.eugenlinde.activitytracker.garmin.model;

import dev.eugenlinde.activitytracker.user.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "garmin_connections")
public class GarminConnection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    @Setter
    private User user;

    @Column(name = "api_key", nullable = false, length = 4096)
    @Getter
    @Setter
    private String apiKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt;

    @Column(name = "last_sync_at")
    @Getter
    @Setter
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_sync_status", length = 16)
    @Getter
    @Setter
    private GarminSyncStatus lastSyncStatus;
}
