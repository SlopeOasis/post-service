package com.slopeoasis.post.repository;

//glavno
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.slopeoasis.post.entity.Posts;
import com.slopeoasis.post.entity.Posts.Tag;

public interface PostsRepo extends JpaRepository<Posts, Integer> {
    //Pageable za paginacijo rezultatov, ala limit, offset, sort
    
    //seller vidi svoje poste ACTIVE in DISABLED, ne pa USER_DELETED
    @Query("SELECT p FROM Posts p WHERE p.sellerId = :sellerId AND p.status != com.slopeoasis.post.entity.Posts.Status.USER_DELETED")
    List<Posts> findBySellerIdExcludingUserDeleted(@Param("sellerId") String sellerId, Pageable pageable);
    
    //kupec vidi svoje nakupe ne glede na status
    @Query("SELECT p FROM Posts p WHERE :buyerId MEMBER OF p.buyers")
    List<Posts> findBoughtPostsByBuyer(@Param("buyerId") String buyerId, Pageable pageable);
    
    //javni prikaz (homepage, filtriranje, iskanje)
    @Query("SELECT p FROM Posts p WHERE :tag MEMBER OF p.tags AND p.status = com.slopeoasis.post.entity.Posts.Status.ACTIVE")
    List<Posts> findByTagAndStatusActive(@Param("tag") Tag tag, Pageable pageable);
    
    //za naključni feed in priporočila
    List<Posts> findByStatus(Posts.Status status, Pageable pageable);
    
    //za homepage z več tagi filtriranje glede na interese uporabnika
    @Query("SELECT p FROM Posts p WHERE p.status = com.slopeoasis.post.entity.Posts.Status.ACTIVE AND " +
           "((:tag1 IS NULL OR :tag1 MEMBER OF p.tags) OR " +
           "(:tag2 IS NULL OR :tag2 MEMBER OF p.tags) OR " +
           "(:tag3 IS NULL OR :tag3 MEMBER OF p.tags))")
    List<Posts> findByMultipleTagsActive(@Param("tag1") Tag tag1, @Param("tag2") Tag tag2, @Param("tag3") Tag tag3, Pageable pageable);

    // Search by title (ACTIVE only)
    @Query("SELECT p FROM Posts p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%')) AND p.status = :status")
    List<Posts> findByTitleContainingIgnoreCaseAndStatus(@Param("title") String title, @Param("status") Posts.Status status, Pageable pageable);

    // Search by title (any status) for internal lookups/Azure access
    @Query("SELECT p FROM Posts p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<Posts> findByTitleContainingIgnoreCase(@Param("title") String title, Pageable pageable);

    // Search by stored file/blob name (ACTIVE only)
    @Query("SELECT p FROM Posts p WHERE LOWER(p.azBlobName) LIKE LOWER(CONCAT('%', :blobName, '%')) AND p.status = :status")
    List<Posts> findByAzBlobNameContainingIgnoreCaseAndStatus(@Param("blobName") String azBlobName, @Param("status") Posts.Status status, Pageable pageable);

    // Search by stored file/blob name (any status) for Azure fetches
    @Query("SELECT p FROM Posts p WHERE LOWER(p.azBlobName) LIKE LOWER(CONCAT('%', :blobName, '%'))")
    List<Posts> findByAzBlobNameContainingIgnoreCase(@Param("blobName") String azBlobName, Pageable pageable);
}