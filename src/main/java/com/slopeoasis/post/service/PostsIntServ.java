package com.slopeoasis.post.service;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.slopeoasis.post.repository.PostsRepo;
import com.slopeoasis.post.entity.Posts;

@Service
public class PostsIntServ {
    private final PostsRepo postsRepo;

    public PostsIntServ(PostsRepo postsRepo) {
        this.postsRepo = postsRepo;
    }

    @Transactional
    public void addBuyer(Integer postId, String buyerClerkId) {
        Posts post = postsRepo.findById(postId)
            .orElseThrow(() -> new IllegalStateException("Post not found"));

        if (post.getBuyers().contains(buyerClerkId)) {
            return; // already has access
        }

        post.getBuyers().add(buyerClerkId);
        postsRepo.save(post);
    }
}
