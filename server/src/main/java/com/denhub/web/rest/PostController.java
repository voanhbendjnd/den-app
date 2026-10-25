package com.denhub.web.rest;

import com.denhub.service.PostService;
import com.denhub.service.dto.DisplayPostDTO;
import com.denhub.service.dto.ResultPaginationCursorDateId;
import com.denhub.service.dto.UpdatePostDTO;
import com.denhub.web.rest.vm.SizeDateIdVM;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class PostController {
    PostService postService;

    @PostMapping("/all-friends")
    public ResponseEntity<ResultPaginationCursorDateId> getAllPostFromFriends(@RequestBody @Valid SizeDateIdVM vm) {
        return ResponseEntity.ok(postService.getAllPost(vm.getSize(), vm.getCursorDate(), vm.getCursorId()));
    }

    @PutMapping("/{postId}")
    public ResponseEntity<DisplayPostDTO> updatePost(
            @PathVariable("postId") Long postId,
            @RequestBody @Valid UpdatePostDTO dto
    ) {
        DisplayPostDTO updated = postService.updatePost(postId, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(@PathVariable("postId") Long postId) {
        postService.deletePost(postId);
        return ResponseEntity.noContent().build();
    }
}

