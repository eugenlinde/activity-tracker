package dev.eugenlinde.activitytracker.diet.model;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "diet_entries")
public class DietEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @Getter
    @Setter
    private User user;

    @Column(nullable = false)
    @Getter
    @Setter
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Getter
    @Setter
    private DietStatus status;

    @Column(length = 500)
    @Getter
    @Setter
    private String comment;

    @CreationTimestamp
    @Getter
    private Instant createdAt;

    @UpdateTimestamp
    @Getter
    private Instant updatedAt;
}
