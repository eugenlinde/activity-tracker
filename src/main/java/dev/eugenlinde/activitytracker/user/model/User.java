package dev.eugenlinde.activitytracker.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;
    @Getter
    @Setter
    @Column(nullable = false)
    private String firstName;
    @Setter
    @Getter
    @Column(nullable = false)
    private String lastName;
    @Getter
    @Setter
    @Column(unique = true, nullable = false)
    private String email;
    @Getter
    @Setter
    @Column(nullable = false)
    private String password;
    @Setter
    @Getter
    private boolean enabled;
    @CreationTimestamp
    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    @Getter
    @Column(nullable = false)
    private Instant updatedAt;
    @Getter
    private Instant deletedAt;

    public User() {
    }
}
