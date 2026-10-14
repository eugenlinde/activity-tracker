package dev.eugenlinde.activitytracker.media.model;

import dev.eugenlinde.activitytracker.activity.model.Activity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "media")
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id", nullable = false)
    @Getter
    @Setter
    private Activity activity;
    @Column(nullable = false, unique = true)
    @Getter
    @Setter
    private String storageKey;
    @Column(nullable = false)
    @Getter
    @Setter
    private String contentType;
}
