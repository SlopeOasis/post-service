package com.slopeoasis.post.service;

import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;

@Service
public class AzureBlobServ {

    private final BlobServiceClient blobServiceClient;
    private final BlobContainerClient containerClient;

    public AzureBlobServ(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.container-name}") String containerName) {
        this.blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        this.containerClient = blobServiceClient.getBlobContainerClient(containerName);
        
        // Create container if it doesn't exist
        if (!containerClient.exists()) {
            containerClient.create();
        }
    }

    /**
     * Upload file to Azure Blob Storage
     * @param file MultipartFile from upload
     * @return unique blob name (UUID-based)
     */
    public String uploadFile(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : "";
        String blobName = UUID.randomUUID().toString() + extension;
        
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        // Set content type
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(file.getContentType());
        
        try (InputStream inputStream = file.getInputStream()) {
            blobClient.upload(BinaryData.fromStream(inputStream, file.getSize()), true);
            blobClient.setHttpHeaders(headers);
        }
        
        return blobName;
    }

    /**
     * Upload file from InputStream with explicit content type
     */
    public String uploadFile(InputStream inputStream, long size, String contentType, String filename) throws Exception {
        String extension = filename != null && filename.contains(".") 
                ? filename.substring(filename.lastIndexOf(".")) 
                : "";
        String blobName = UUID.randomUUID().toString() + extension;
        
        BlobClient blobClient = containerClient.getBlobClient(blobName);
        
        BlobHttpHeaders headers = new BlobHttpHeaders()
                .setContentType(contentType);
        
        blobClient.upload(BinaryData.fromStream(inputStream, size), true);
        blobClient.setHttpHeaders(headers);
        
        return blobName;
    }

    /**
     * Get blob properties (name, type, size)
     */
    public Optional<BlobMetadata> getBlobMetadata(String blobName) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                return Optional.empty();
            }
            
            BlobProperties properties = blobClient.getProperties();
            
            return Optional.of(new BlobMetadata(
                blobName,
                properties.getContentType(),
                properties.getBlobSize(),
                properties.getCreationTime(),
                properties.getLastModified()
            ));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Download blob as InputStream
     */
    public Optional<InputStream> downloadBlob(String blobName) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                return Optional.empty();
            }
            return Optional.of(blobClient.openInputStream());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Generate SAS URL for blob (time-limited download link)
     * @param blobName blob name
     * @param expirationMinutes how many minutes the link is valid
     * @return SAS URL string
     */
    public Optional<String> generateSasUrl(String blobName, int expirationMinutes) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                return Optional.empty();
            }
            
            BlobSasPermission permissions = new BlobSasPermission().setReadPermission(true);
            OffsetDateTime expiryTime = OffsetDateTime.now().plus(Duration.ofMinutes(expirationMinutes));
            
            BlobServiceSasSignatureValues sasValues = new BlobServiceSasSignatureValues(expiryTime, permissions);
            String sasToken = blobClient.generateSas(sasValues);
            
            return Optional.of(blobClient.getBlobUrl() + "?" + sasToken);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Delete blob from storage
     */
    public boolean deleteBlob(String blobName) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            if (!blobClient.exists()) {
                return false;
            }
            blobClient.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if blob exists
     */
    public boolean blobExists(String blobName) {
        try {
            BlobClient blobClient = containerClient.getBlobClient(blobName);
            return blobClient.exists();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * DTO for blob metadata
     */
    public static class BlobMetadata {
        public final String name;
        public final String contentType;
        public final long sizeBytes;
        public final OffsetDateTime createdAt;
        public final OffsetDateTime lastModified;

        public BlobMetadata(String name, String contentType, long sizeBytes, 
                           OffsetDateTime createdAt, OffsetDateTime lastModified) {
            this.name = name;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
            this.createdAt = createdAt;
            this.lastModified = lastModified;
        }

        public String getSizeFormatted() {
            if (sizeBytes < 1024) return sizeBytes + " B";
            if (sizeBytes < 1024 * 1024) return String.format("%.2f KB", sizeBytes / 1024.0);
            if (sizeBytes < 1024 * 1024 * 1024) return String.format("%.2f MB", sizeBytes / (1024.0 * 1024));
            return String.format("%.2f GB", sizeBytes / (1024.0 * 1024 * 1024));
        }
    }
}
