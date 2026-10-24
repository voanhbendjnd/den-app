package com.denhub.web.rest;

import com.denhub.service.LikeService;
import com.denhub.service.dto.LikeResponseDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE,  makeFinal = true)
public class PostLikeController {
    LikeService likeService;
    @PostMapping("/{postId}/like")
    public ResponseEntity<LikeResponseDTO> toggleLike(
            @PathVariable Long postId) {

        LikeResponseDTO response = likeService.toggleLike(postId);
        return ResponseEntity.ok(response);
    }
}
