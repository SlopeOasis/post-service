package com.slopeoasis.post.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.slopeoasis.post.entity.Rating;

public interface RatingRepo extends JpaRepository<Rating, Long> {
    Optional<Rating> findByPostIdAndBuyerId(Integer postId, String buyerId);
    List<Rating> findByPostId(Integer postId);

    @Query("SELECT AVG(r.rating) FROM Rating r WHERE r.postId = :postId")
    Double averageForPost(@Param("postId") Integer postId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.postId = :postId")
    Long countForPost(@Param("postId") Integer postId);
}
