package com.pheme.phemenotify.persistence.entity;

import jakarta.persistence.*;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @ElementCollection(targetClass = Channel.class ,fetch = FetchType.EAGER)
    @CollectionTable(
            name = "user_preference_channels",
            joinColumns = @JoinColumn(name = "preference_id")
    )
    @Column(name = "channel")
    @Builder.Default
    private Set<Channel>  enabledChannels = new HashSet<>();

    @Builder.Default
    @Column(name = "locale", nullable = false)
    private String locale = "en";

    @Builder.Default
    @Column(name = "timezone", nullable = false)
    private String timezone = "UTC";

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
