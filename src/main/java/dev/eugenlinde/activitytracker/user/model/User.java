package dev.eugenlinde.activitytracker.user.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
    private boolean tokenExpired;
    @Column(nullable = false)
    private long createdAt;
    @Column(nullable = false)
    private long updatedAt;
    private long deletedAt;

    public User() {
    }
}
