package com.denhub.web.rest;

import com.denhub.service.PostService;
import com.denhub.service.dto.PostDTO;
import com.denhub.service.dto.ResultPaginationCursorDateId;
import com.denhub.web.rest.vm.SizeDateIdVM;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class PostController {
    PostService postService;
    @PostMapping("/all-friends")
    public ResponseEntity<ResultPaginationCursorDateId> getAllPostFromFriends(@RequestBody @Valid SizeDateIdVM vm){
        return ResponseEntity.ok(postService.getAllPost(vm.getSize(), vm.getCursorDate(), vm.getCursorId()));
    }
}
