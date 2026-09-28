package dev.eugenlinde.activitytracker.activity.model;

import dev.eugenlinde.activitytracker.user.model.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "activities")
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @Getter
    @Setter
    private ActivityType activityType;
    @Column(nullable = false)
    @Getter
    @Setter
    private Duration duration;
    @Column()
    @Getter
    @Setter
    private String notes;
    @Column()
    @Getter
    @Setter
    private Instant startedAt;
    @CreationTimestamp
    @Getter
    private Instant createdAt;
    @UpdateTimestamp
    @Getter
    private Instant updatedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    @Setter
    private User user;
}
