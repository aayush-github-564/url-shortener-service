package com.aayush.url_shortener.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "clicks", indexes = {
    @Index(name = "idx_click_url_id", columnList = "url_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "url_id", nullable = false)
    private Url url;

    @Column(nullable = false)
    private LocalDateTime clickedAt;

    @Column
    private String ipAddress;

    @Column
    private String country;

    @Column
    private String deviceType;

    @Column
    private String browser;

    @Column
    private String referer;

    @PrePersist
    public void prePersist() {
        this.clickedAt = LocalDateTime.now();
    }
}