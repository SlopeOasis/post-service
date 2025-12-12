package com.slopeoasis.post.entity;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "posts")
public class Posts {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    // Basic info
    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String sellerId;  // ID from Clerk, or your user service

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    // Tags
    @ElementCollection
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Tag> tags = new HashSet<>();

    // Main file stored in Azure Blob Storage
    @Column(nullable = false, unique = true)
    private String azBlobName;

    // Version of the uploaded file (increments when seller replaces the file)
    @Column(nullable = false)
    private Integer fileVersion = 1;

    // Preview images
    @ElementCollection
    @CollectionTable(name = "post_preview_images", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "preview_blob_name")
    private List<String> previewImages;

    // Buyers (Clerk IDs or your user IDs)
    @ElementCollection
    @CollectionTable(name = "post_buyers", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "buyer_id")
    private Set<String> buyers;

    // Copies: -1 means unlimited
    @Column(nullable = false)
    private Integer copies;

    // Price stored in USD
    @Column(nullable = false)
    private Double priceUSD;

    @CreationTimestamp
    private LocalDateTime uploadTime;

    @UpdateTimestamp
    private LocalDateTime lastTimeModified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status {//ACTIVE pomen aktiven post, DISABLED je onemogocen, ni viden na prodajnih, USER_DELETED pomen da ga ni vec na prodaji ampak tut user ga ne vid pod owned, medtem ko ga buyerji sekr majo
        ACTIVE,
        DISABLED,
        USER_DELETED
    }

    public Posts() {//konstruktor da JPA ne crkne,
    }

    //Getters/Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Set<Tag> getTags() { return tags; }
    public void setTags(Set<Tag> tags) { this.tags = tags; }

    public String getAzBlobName() { return azBlobName; }
    public void setAzBlobName(String azBlobName) { this.azBlobName = azBlobName; }

    public Integer getFileVersion() { return fileVersion; }
    public void setFileVersion(Integer fileVersion) { this.fileVersion = fileVersion; }

    public List<String> getPreviewImages() { return previewImages; }
    public void setPreviewImages(List<String> previewImages) { this.previewImages = previewImages; }

    public Set<String> getBuyers() { return buyers; }
    public void setBuyers(Set<String> buyers) { this.buyers = buyers; }

    public Integer getCopies() { return copies; }
    public void setCopies(Integer copies) { this.copies = copies; }

    public Double getPriceUSD() { return priceUSD; }
    public void setPriceUSD(Double priceUSD) { this.priceUSD = priceUSD; }

    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }

    public LocalDateTime getLastTimeModified() { return lastTimeModified; }
    public void setLastTimeModified(LocalDateTime lastTimeModified) { this.lastTimeModified = lastTimeModified; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    

    //tag list
    public enum Tag {
        ART,
        MUSIC,
        VIDEO,
        CODE,
        TEMPLATE,
        PHOTO,
        MODEL_3D,
        FONT,
        OTHER
    }
}

