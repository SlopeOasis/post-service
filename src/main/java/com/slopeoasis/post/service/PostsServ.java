package com.slopeoasis.post.service;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.slopeoasis.post.entity.Posts;
import com.slopeoasis.post.entity.Posts.Status;
import com.slopeoasis.post.entity.Posts.Tag;
import com.slopeoasis.post.entity.Rating;
import com.slopeoasis.post.repository.PostsRepo;
import com.slopeoasis.post.repository.RatingRepo;

@Service
public class PostsServ {
    private final PostsRepo postsRepo;
    private final RatingRepo ratingRepo;

    public PostsServ(PostsRepo postsRepo, RatingRepo ratingRepo) {
        this.postsRepo = postsRepo;
        this.ratingRepo = ratingRepo;
    }

    //Create new post (default ACTIVE)
    public Posts createPost(Posts post) {
        if (post.getBuyers() == null) post.setBuyers(new java.util.HashSet<>());
        if (post.getTags() == null) post.setTags(new java.util.HashSet<>());
        if (post.getStatus() == null) post.setStatus(Status.ACTIVE);
        if (post.getFileVersion() == null) post.setFileVersion(1);
        return postsRepo.save(post);
    }

    //Change post status (seller lahko oznaci kot ACTIVE/DISABLED, v primeru ko jo "izbriše" se spremeni le status na USER_DELETED)
    @Transactional
    public Optional<Posts> changePostStatus(Integer postId, String sellerId, Status newStatus) {
        Optional<Posts> opt = postsRepo.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Posts p = opt.get();
        if (!p.getSellerId().equals(sellerId)) return Optional.empty();
        p.setStatus(newStatus);
        return Optional.of(postsRepo.save(p));
    }

    //Spremeni osnovne podatke posta (title, description, tags, previewImages, priceUSD, copies)
    @Transactional
    public Optional<Posts> editPost(Integer postId, String sellerId, Posts updates) {
        Optional<Posts> opt = postsRepo.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Posts p = opt.get();
        if (!p.getSellerId().equals(sellerId)) return Optional.empty();

        if (updates.getTitle() != null) p.setTitle(updates.getTitle());
        if (updates.getDescription() != null) p.setDescription(updates.getDescription());
        if (updates.getTags() != null && !updates.getTags().isEmpty()) p.setTags(updates.getTags());
        if (updates.getPreviewImages() != null) p.setPreviewImages(updates.getPreviewImages());
        if (updates.getPriceUSD() != null) p.setPriceUSD(updates.getPriceUSD());
        if (updates.getCopies() != null) p.setCopies(updates.getCopies());

        return Optional.of(postsRepo.save(p));
    }

    //Posodobi glavno datoteko (poveča fileVersion)
    @Transactional
    public Optional<Posts> updatePostFile(Integer postId, String sellerId, String newBlobName) {
        Optional<Posts> opt = postsRepo.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Posts p = opt.get();
        if (!p.getSellerId().equals(sellerId)) return Optional.empty();
        p.setAzBlobName(newBlobName);
        p.setFileVersion(p.getFileVersion() + 1);
        return Optional.of(postsRepo.save(p));
    }

    //Seller's posts (ACTIVE + DISABLED, ne USER_DELETED)
    public java.util.List<Posts> getPostsBySeller(String sellerId, Pageable pageable) {
        return postsRepo.findBySellerIdExcludingUserDeleted(sellerId, pageable);
    }

    //Buyer's purchases (vsi statusi)
    public java.util.List<Posts> getPostsByBuyer(String buyerId, Pageable pageable) {
        return postsRepo.findBoughtPostsByBuyer(buyerId, pageable);
    }

    //Javni po tagu (ACTIVE) za filtriranje
    public java.util.List<Posts> getPostsByTag(Tag tag, Pageable pageable) {
        return postsRepo.findByTagAndStatusActive(tag, pageable);
    }

    //Javni po tagu (ACTIVE) brez pagingiranja - za interno uporabo
    public java.util.List<Posts> getPostsByTag(Tag tag) {
        return postsRepo.findByTagAndStatusActive(tag);
    }

    //Javni po temah/interesih (ACTIVE) – ustreza kateremukoli od treh tagov
    public java.util.List<Posts> getPostsByThemes(Tag t1, Tag t2, Tag t3, Pageable pageable) {
        return postsRepo.findByMultipleTagsActive(t1, t2, t3, pageable);
    }

    //Post info
    public Optional<Posts> getPostInfo(Integer postId) {
        return postsRepo.findById(postId);
    }

    // Search posts by title (ACTIVE)
    public java.util.List<Posts> searchByTitle(String title, Pageable pageable) {
        return postsRepo.findByTitleContainingIgnoreCaseAndStatus(title, Status.ACTIVE, pageable);
    }

    // Search posts by title (any status) for internal/Azure lookups
    public java.util.List<Posts> searchByTitleAnyStatus(String title, Pageable pageable) {
        return postsRepo.findByTitleContainingIgnoreCase(title, pageable);
    }

    // Search posts by stored blob/file name (ACTIVE)
    public java.util.List<Posts> searchByBlobName(String blobName, Pageable pageable) {
        return postsRepo.findByAzBlobNameContainingIgnoreCaseAndStatus(blobName, Status.ACTIVE, pageable);
    }

    // Search posts by stored blob/file name (any status) for Azure retrieval
    public java.util.List<Posts> searchByBlobNameAnyStatus(String blobName, Pageable pageable) {
        return postsRepo.findByAzBlobNameContainingIgnoreCase(blobName, pageable);
    }

    //Dodaj kupca (idempotentno) in zmanjša število kopij, če je >0. Ni dovoljeno, če je USER_DELETED ali kopij == 0.
    @Transactional
    public Optional<Posts> addBuyer(Integer postId, String buyerId) {
        Optional<Posts> opt = postsRepo.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Posts p = opt.get();

        if (p.getStatus() == Status.USER_DELETED) return Optional.empty();

        if (p.getCopies() != null && p.getCopies() == 0) {
            return Optional.empty(); // out of stock
        }

        if (p.getBuyers() == null) {
            p.setBuyers(new java.util.HashSet<>());
        }

        // Idempotent add
        if (!p.getBuyers().contains(buyerId)) {
            p.getBuyers().add(buyerId);
            if (p.getCopies() != null && p.getCopies() > 0) {
                p.setCopies(p.getCopies() - 1);
            }
        }

        return Optional.of(postsRepo.save(p));
    }

    // Check availability (copies and status)
    public Optional<Availability> checkAvailability(Integer postId) {
        Optional<Posts> opt = postsRepo.findById(postId);
        if (opt.isEmpty()) return Optional.empty();
        Posts p = opt.get();
        boolean available = p.getStatus() == Status.ACTIVE && (p.getCopies() == null || p.getCopies() > 0 || p.getCopies() == -1);
        return Optional.of(new Availability(available, p.getCopies(), p.getStatus()));
    }

    // Submit rating (buyer must have purchased) - idempotent per buyer/post
    @Transactional
    public boolean submitRating(Integer postId, String buyerId, int ratingValue) {
        if (ratingValue < 1 || ratingValue > 5) return false;
        Optional<Posts> opt = postsRepo.findById(Math.toIntExact(postId));
        if (opt.isEmpty()) return false;
        Posts p = opt.get();
        if (p.getBuyers() == null || !p.getBuyers().contains(buyerId)) return false;

        Optional<Rating> existing = ratingRepo.findByPostIdAndBuyerId(postId, buyerId);
        if (existing.isPresent()) {
            Rating r = existing.get();
            r.setRating(ratingValue);
            ratingRepo.save(r);
            return true;
        }

        Rating r = new Rating(postId, buyerId, ratingValue);
        ratingRepo.save(r);
        return true;
    }

    // Get ratings for a post (list)
    public java.util.List<Rating> getRatings(Integer postId) {
        return ratingRepo.findByPostId(postId);
    }

    // Get rating summary (avg + count)
    public RatingSummary getRatingSummary(Integer postId) {
        Double avg = ratingRepo.averageForPost(postId);
        Long count = ratingRepo.countForPost(postId);
        return new RatingSummary(avg != null ? avg : 0.0, count != null ? count : 0L);
    }

    // DTO for availability
    public static class Availability {
        public final boolean available;
        public final Integer copies;
        public final Status status;
        public Availability(boolean available, Integer copies, Status status) {
            this.available = available;
            this.copies = copies;
            this.status = status;
        }
    }

    // DTO for rating summary
    public static class RatingSummary {
        public final double average;
        public final long count;
        public RatingSummary(double average, long count) {
            this.average = average;
            this.count = count;
        }
    }
}
