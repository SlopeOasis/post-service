package com.slopeoasis.post.controller;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;

import com.slopeoasis.post.entity.Posts;
import com.slopeoasis.post.entity.Posts.Status;
import com.slopeoasis.post.entity.Posts.Tag;
import com.slopeoasis.post.entity.Rating;
import com.slopeoasis.post.service.AzureBlobServ;
import com.slopeoasis.post.service.PostsServ;
import com.slopeoasis.post.service.PostsServ.RatingSummary;

@RestController
@RequestMapping("/posts")
public class PostsCont {

    private final PostsServ postsServ;
    private final AzureBlobServ azureBlobServ;
    private final RestTemplate restTemplate;
    private final String internalApiKey;
    private final String userApiUrl;
    private final int defaultSasMinutes;
    private final int minSasMinutes;
    private final int maxSasMinutes;

    public PostsCont(PostsServ postsServ, AzureBlobServ azureBlobServ, RestTemplate restTemplate,
                     @org.springframework.beans.factory.annotation.Value("${sas.default.minutes:60}") int defaultSasMinutes,
                     @org.springframework.beans.factory.annotation.Value("${sas.min.minutes:1}") int minSasMinutes,
                     @org.springframework.beans.factory.annotation.Value("${sas.max.minutes:120}") int maxSasMinutes,
                     @Value("${internal.api.key:}") String internalApiKey,
                     @Value("${user.api.url:http://localhost:8080}") String userApiUrl) {
        this.postsServ = postsServ;
        this.azureBlobServ = azureBlobServ;
        this.restTemplate = restTemplate;
        this.defaultSasMinutes = defaultSasMinutes;
        this.minSasMinutes = minSasMinutes;
        this.maxSasMinutes = maxSasMinutes;
        this.internalApiKey = internalApiKey;
        this.userApiUrl = userApiUrl;
    }

    //za kreiranje novih objav
    @PostMapping
    public ResponseEntity<?> createPost(
                                        @RequestParam("title") String title,
                                        @RequestParam("description") String description,
                                        @RequestParam("priceUSD") Double priceUSD,
                                        @RequestParam("copies") Integer copies,
                                        @RequestParam("tags") String tagsStr,
                                        @RequestParam("status") String statusStr,
                                        @RequestParam("file") MultipartFile mainFile,
                                        @RequestParam(value = "previewImages", required = false) List<MultipartFile> previewImages,
                                        @RequestAttribute(name = "X-User-Id", required = false) String userId) throws IOException {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        
        // Parse tags from comma-separated string
        List<String> tagsList = tagsStr != null && !tagsStr.isBlank() 
            ? java.util.Arrays.asList(tagsStr.split(",")) 
            : new java.util.ArrayList<>();
        
        // Validate tag format
        Optional<Set<Tag>> tagsOpt = parseTags(tagsList);
        if (tagsOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid tag value");
        
        // Validate and convert status
        Status status;
        try {
            status = Status.valueOf(statusStr);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid status");
        }
        
        // Upload main file to Azure
        if (mainFile == null || mainFile.isEmpty()) {
            return ResponseEntity.badRequest().body("Main file is required");
        }
        String mainBlobName;
        try {
            mainBlobName = azureBlobServ.uploadFile(mainFile);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload main file");
        }
        
        // Upload preview images to Azure
        List<String> previewBlobNames = new java.util.ArrayList<>();
        if (previewImages != null) {
            for (MultipartFile preview : previewImages) {
                if (preview != null && !preview.isEmpty()) {
                    try {
                        previewBlobNames.add(azureBlobServ.uploadFile(preview));
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload preview image");
                    }
                }
            }
        }
        
        // Create the post
        Posts p = new Posts();
        p.setTitle(title);
        p.setSellerId(userId);
        p.setDescription(description);
        p.setTags(tagsOpt.orElseGet(java.util.HashSet::new));
        p.setAzBlobName(mainBlobName);
        p.setPreviewImages(previewBlobNames);
        p.setPriceUSD(priceUSD);
        p.setCopies(copies);
        p.setStatus(status);
        
        return new ResponseEntity<>(postsServ.createPost(p), HttpStatus.CREATED);
    }

    //za urejanje obstoječih objav
    @PutMapping("/{id}")
    public ResponseEntity<?> editPost(@PathVariable Integer id, @RequestBody UpdatePostRequest req,
                                      @RequestAttribute(name = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Optional<Set<Tag>> tagsOpt = parseTags(req.tags);
        if (req.tags != null && tagsOpt.isEmpty()) return ResponseEntity.badRequest().body("Invalid tag value");

        Posts updates = new Posts();
        updates.setTitle(req.title);
        updates.setDescription(req.description);
        updates.setPreviewImages(req.previewImages);
        updates.setPriceUSD(req.priceUSD);
        updates.setCopies(req.copies);
        if (req.tags != null) updates.setTags(tagsOpt.orElseGet(java.util.HashSet::new));

        return postsServ.editPost(id, userId, updates)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed or post not found"));
    }

    //za spreminjanje statusa objav
    @PutMapping("/{id}/status")
    public ResponseEntity<?> changeStatus(@PathVariable Integer id, @RequestBody StatusChangeRequest req,
                                          @RequestAttribute(name = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Status newStatus;
        try { newStatus = Status.valueOf(req.status); } catch (Exception e) { return ResponseEntity.badRequest().body("Invalid status"); }
        return postsServ.changePostStatus(id, userId, newStatus)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed or post not found"));
    }

    //za posodabljanje glavne datoteke posta
    @PutMapping("/{id}/file")
    public ResponseEntity<?> updateFile(@PathVariable Integer id, @RequestBody FileUpdateRequest req,
                                        @RequestAttribute(name = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return postsServ.updatePostFile(id, userId, req.newBlobName)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed or post not found"));
    }

    //za posodabljanje glavne datoteke posta (multipart upload nove datoteke)
    @PutMapping(path = "/{id}/file-multipart", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateFileMultipart(@PathVariable Integer id,
                                                 @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                 @RequestAttribute(name = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("File is required");
        try {
            String newBlob = azureBlobServ.uploadFile(file);
            return postsServ.updatePostFile(id, userId, newBlob)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed or post not found"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload file");
        }
    }

    //za pridobivanje info o postu glede na id
    @GetMapping("/{id}")
    public ResponseEntity<?> getPost(@PathVariable Integer id) {
        Optional<Posts> postOpt = postsServ.getPostInfo(id);
        if (postOpt.isEmpty()) return ResponseEntity.notFound().build();
        RatingSummary summary = postsServ.getRatingSummary(id);
        return ResponseEntity.ok(new PostWithRating(postOpt.get(), summary));
    }

    //za preverjanje ali je post na voljo za nakup
    @GetMapping("/{id}/availability")
    public ResponseEntity<?> availability(@PathVariable Integer id) {
        return postsServ.checkAvailability(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //za dodajanje kupca k postu (po nakupu)
    @PostMapping("/{id}/buyers")
    public ResponseEntity<?> addBuyer(@PathVariable Integer id, @RequestBody AddBuyerRequest req,
                                      @RequestParam(name = "key", required = false) String key) {
        if (internalApiKey != null && !internalApiKey.isBlank()) {
            if (key == null || !internalApiKey.equals(key)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden");
            }
        }
        return postsServ.addBuyer(id, req.buyerId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().body("Cannot add buyer or post not found"));
    }

    //za pridobivanje postov določenega sellerja
    @GetMapping("/seller/{sellerId}")
    public List<Posts> bySeller(@PathVariable String sellerId,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return postsServ.getPostsBySeller(sellerId, pageable);
    }

    //za pridobivanje postov ki jih je kupil določen buyer
    @GetMapping("/buyer/{buyerId}")
    public List<Posts> byBuyer(@PathVariable String buyerId,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return postsServ.getPostsByBuyer(buyerId, pageable);
    }

    //za pridobivanje postov po tagu
    @GetMapping("/tag/{tag}")
    public ResponseEntity<?> byTag(@PathVariable String tag,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        Tag t;
        try { t = Tag.valueOf(tag); } catch (Exception e) { return ResponseEntity.badRequest().body("Invalid tag"); }
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(postsServ.getPostsByTag(t, pageable));
    }

    //za iskanje postov po naslovu
    @GetMapping("/search/title")
    public ResponseEntity<?> searchTitle(@RequestParam String q,
                                         @RequestParam(defaultValue = "false") boolean anyStatus,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (anyStatus) {
            return ResponseEntity.ok(postsServ.searchByTitleAnyStatus(q, pageable));
        }
        return ResponseEntity.ok(postsServ.searchByTitle(q, pageable));
    }

    //za iskanje postov po imenu shranjene datoteke/blob-a, lahko jih dobimo več ker imamo partial match imen
    @GetMapping("/search/blob")
    public ResponseEntity<?> searchBlob(@RequestParam String q,
                                        @RequestParam(defaultValue = "false") boolean anyStatus,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        if (anyStatus) {
            return ResponseEntity.ok(postsServ.searchByBlobNameAnyStatus(q, pageable));
        }
        return ResponseEntity.ok(postsServ.searchByBlobName(q, pageable));
    }

    //za pridobivanje postov glede na uporabnikove teme/interese, uporabimo user API
    @GetMapping("/themes")
    public ResponseEntity<?> byThemes(@RequestAttribute(name = "X-User-Id", required = false) String userId,
                                      @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing user ID");
        }
        
        try {
            // Call user API to get user's themes, forwarding the auth token
            String userApiThemesUrl = userApiUrl + "/users/themes";
            
            HttpHeaders headers = new HttpHeaders();
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                headers.set("Authorization", authHeader);
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String[]> themesResponse = restTemplate.exchange(
                userApiThemesUrl, 
                org.springframework.http.HttpMethod.GET, 
                entity, 
                String[].class
            );
            
            if (!themesResponse.hasBody() || themesResponse.getBody() == null) {
                return ResponseEntity.ok(List.of());
            }
            
            String[] themes = themesResponse.getBody();
            List<Posts> allPosts = new java.util.ArrayList<>();
            
            // Fetch posts for each theme
            for (String theme : themes) {
                if (theme != null && !theme.isEmpty()) {
                    try {
                        List<Posts> postsForTheme = postsServ.getPostsByTag(Posts.Tag.valueOf(theme.toUpperCase()));
                        allPosts.addAll(postsForTheme);
                    } catch (IllegalArgumentException e) {
                        System.out.println("[PostsCont] Invalid theme: " + theme);
                        // Skip invalid themes
                    }
                }
            }
            
            return ResponseEntity.ok(allPosts);
        } catch (Exception e) {
            System.err.println("[PostsCont] Error fetching themes/posts: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to fetch posts by user themes");
        }
    }

    //za ocenjevanje posta s strani kupca
    @PostMapping("/{id}/ratings")
    public ResponseEntity<?> rate(@PathVariable Integer id, @RequestBody RatingRequest req) {
        if (!postsServ.submitRating(id, req.buyerId, req.rating)) {
            return ResponseEntity.badRequest().body("Rating failed (not buyer, invalid rating, or post missing)");
        }
        return ResponseEntity.ok().build();
    }

    //za pridobivanje vseh ocen posta
    @GetMapping("/{id}/ratings")
    public ResponseEntity<List<Rating>> ratings(@PathVariable Integer id) {
        return ResponseEntity.ok(postsServ.getRatings(id));
    }

    //za pridobivanje average ocene in števila ocen posta
    @GetMapping("/{id}/ratings/summary")
    public ResponseEntity<RatingSummary> ratingSummary(@PathVariable Integer id) {
        return ResponseEntity.ok(postsServ.getRatingSummary(id));
    }

    //za pridobivanje metapodatkov datoteke posta iz Azure Blob Storage, ala velikost, tip
    @GetMapping("/{id}/blob-metadata")
    public ResponseEntity<?> blobMetadata(@PathVariable Integer id,
                                          @RequestParam(required = false) String blobName) {
        Optional<Posts> postOpt = postsServ.getPostInfo(id);
        if (postOpt.isEmpty()) return ResponseEntity.notFound().build();
        String effectiveBlob = blobName != null ? blobName : postOpt.get().getAzBlobName();
        return azureBlobServ.getBlobMetadata(effectiveBlob)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //za zamenjavo predoglednih slik (multipart upload), prepiše celoten seznam previewImages
    @PutMapping(path = "/{id}/previews-multipart", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> replacePreviews(@PathVariable Integer id,
                                             @RequestParam(value = "previewImages", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> previewImages,
                                             @RequestAttribute(name = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        java.util.List<String> previewBlobNames = new java.util.ArrayList<>();
        if (previewImages != null) {
            for (org.springframework.web.multipart.MultipartFile preview : previewImages) {
                if (preview != null && !preview.isEmpty()) {
                    try {
                        previewBlobNames.add(azureBlobServ.uploadFile(preview));
                    } catch (Exception e) {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to upload preview image");
                    }
                }
            }
        }
        Posts updates = new Posts();
        updates.setPreviewImages(previewBlobNames);
        return postsServ.editPost(id, userId, updates)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed or post not found"));
    }

    //za generiranje časovno omejene SAS povezave za prenos datoteke
    //osnovna avtorizacija: dovoli le prodajalcu ali kupcu tega posta, casovno imejen na 60 minut privzeto, v application.properties/.env
    @GetMapping("/{id}/blob-sas")
    public ResponseEntity<?> blobSas(@PathVariable Integer id,
                                     @RequestParam(required = false) Integer minutes,
                                     @RequestParam(required = false) String blobName,
                                     @RequestAttribute(name = "X-User-Id", required = false) String userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Optional<Posts> postOpt = postsServ.getPostInfo(id);
        if (postOpt.isEmpty()) return ResponseEntity.notFound().build();
        Posts post = postOpt.get();

        boolean isSeller = post.getSellerId() != null && post.getSellerId().equals(userId);
        boolean isBuyer = post.getBuyers() != null && post.getBuyers().contains(userId);
        if (!isSeller && !isBuyer) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Not allowed");
        }

        String effectiveBlob = blobName != null ? blobName : post.getAzBlobName();
        if (blobName != null) {
            boolean allowedBlob = effectiveBlob.equals(post.getAzBlobName()) ||
                    (post.getPreviewImages() != null && post.getPreviewImages().contains(effectiveBlob));
            if (!allowedBlob) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Blob not allowed for this post");
            }
        }

        int requested = (minutes == null) ? defaultSasMinutes : minutes;
        int ttl = Math.max(minSasMinutes, Math.min(requested, maxSasMinutes));
        return azureBlobServ.generateSasUrl(effectiveBlob, ttl)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/public-sas")
    public ResponseEntity<?> publicSas(@PathVariable Integer id,
                                       @RequestParam(required = false) Integer minutes,
                                       @RequestParam(required = false) String blobName) {
        Optional<Posts> postOpt = postsServ.getPostInfo(id);
        if (postOpt.isEmpty()) return ResponseEntity.notFound().build();
        Posts post = postOpt.get();

        // Only allow ACTIVE posts for public access
        if (post.getStatus() != Posts.Status.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Post not available for public access");
        }

        String effectiveBlob = blobName != null ? blobName : post.getAzBlobName();
        if (blobName != null) {
            boolean allowedBlob = effectiveBlob.equals(post.getAzBlobName()) ||
                    (post.getPreviewImages() != null && post.getPreviewImages().contains(effectiveBlob));
            if (!allowedBlob) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Blob not allowed for this post");
            }
        }

        int requested = (minutes == null) ? defaultSasMinutes : minutes;
        int ttl = Math.max(minSasMinutes, Math.min(requested, maxSasMinutes));
        return azureBlobServ.generateSasUrl(effectiveBlob, ttl)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //to so neke pomožne metode
    private Optional<Set<Tag>> parseTags(List<String> tags) {
        if (tags == null) return Optional.of(new java.util.HashSet<>());
        try {
            Set<Tag> parsed = tags.stream()
                    .map(Tag::valueOf)
                    .collect(Collectors.toSet());
            return Optional.of(parsed);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Tag parseTagNullable(String tag) {
        if (tag == null) return null;
        try { return Tag.valueOf(tag); } catch (Exception e) { return null; }
    }

    public static class CreatePostRequest {
        public String title;
        public String sellerId;
        public String description;
        public List<String> tags;
        public String azBlobName;
        public List<String> previewImages;
        public Double priceUSD;
        public Integer copies;
    }

    public static class UpdatePostRequest {
        public String sellerId;
        public String title;
        public String description;
        public List<String> tags;
        public List<String> previewImages;
        public Double priceUSD;
        public Integer copies;
    }

    public static class StatusChangeRequest {
        public String sellerId;
        public String status;
    }

    public static class FileUpdateRequest {
        public String sellerId;
        public String newBlobName;
    }

    public static class AddBuyerRequest {
        public String buyerId;
    }

    public static class RatingRequest {
        public String buyerId;
        public int rating;
    }

    public static class PostWithRating {
        public final Posts post;
        public final RatingSummary ratingSummary;
        public PostWithRating(Posts post, RatingSummary ratingSummary) {
            this.post = post;
            this.ratingSummary = ratingSummary;
        }
    }
}
