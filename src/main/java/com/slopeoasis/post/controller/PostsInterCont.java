package com.slopeoasis.post.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slopeoasis.post.service.PostsIntServ;

@RestController
@RequestMapping("/internal/posts")
public class PostsInterCont {
     private final PostsIntServ postService;

    public PostsInterCont(PostsIntServ postService) {
        this.postService = postService;
    }

    public record GrantAccessRequest(
            String buyerClerkId,
            UUID paymentIntentId
    ) {}

    @PostMapping("/{postId}/grant-access")
    public ResponseEntity<Void> grantAccess(
            @PathVariable Integer postId,
            @RequestBody GrantAccessRequest req
    ) {
        postService.addBuyer(postId, req.buyerClerkId());
        return ResponseEntity.ok().build();
    }
}
