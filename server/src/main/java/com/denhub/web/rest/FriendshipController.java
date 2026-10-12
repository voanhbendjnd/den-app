package com.denhub.web.rest;

import com.denhub.service.FriendshipService;
import com.denhub.web.rest.vm.FriendshipVM;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friendships")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendshipController {
    FriendshipService friendshipService;

    @PostMapping({"/requests", "/sent-request-received"})
    public ResponseEntity<Void> sendFriendRequest(@Valid @RequestBody FriendshipVM vm) {
        friendshipService.sendRequestAddFriend(vm.getFriendId());
        return ResponseEntity.ok().build();
    }
}
