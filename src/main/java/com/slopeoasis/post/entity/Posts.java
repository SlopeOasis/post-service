package com.slopeoasis.post.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "posts")
public class Posts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Basic info
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Long sellerId;  // ID from Clerk, or your user service

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Tags
    @ElementCollection
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", nullable = false)
    private Set<String> tags;

    // Main file stored in Azure Blob Storage
    @Column(nullable = false, unique = true)
    private String azBlobName;

    // Preview images
    @ElementCollection
    @CollectionTable(name = "post_preview_images", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "preview_blob_name")
    private List<String> previewImages;

    // Buyers (Clerk IDs or your user IDs)
    @ElementCollection
    @CollectionTable(name = "post_buyers", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "buyer_id")
    private Set<Long> buyers;

    // Copies: -1 means unlimited
    @Column(nullable = false)
    private Integer copies;

    // Price stored in USD
    @Column(nullable = false)
    private Double priceUSD;

    @CreationTimestamp
    private LocalDateTime uploadTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status {//ACTIVE pomen aktiven post, DISABLED je onemogocen, ni viden na prodajnih, USER_DELETED pomen da ga ni vec na prodaji ampak tut user ga ne vid pod owned, medtem ko ga buyerji sekr majo
        ACTIVE,
        DISABLED,
        USER_DELETED
    }
}