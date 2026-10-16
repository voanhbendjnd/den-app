package com.denhub.web.rest;

import com.denhub.service.FriendshipService;
import com.denhub.service.dto.ResultPaginationCursorTime;
import com.denhub.service.realtime.FriendshipAction;
import com.denhub.web.rest.vm.FriendshipVM;
import com.denhub.web.rest.vm.SizeAndCursorTimeVM;
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

    @PostMapping("/friend-requests")
    public ResponseEntity<ResultPaginationCursorTime> getFriendRequest(@RequestBody @Valid SizeAndCursorTimeVM vm) {
        return ResponseEntity.ok(friendshipService.getAllFriendRequests(vm.getSize(), vm.getCursor()));
    }
    @PostMapping("/all-friend")
    public ResponseEntity<ResultPaginationCursorTime> getAllFriend(@RequestBody @Valid SizeAndCursorTimeVM vm) {
        return ResponseEntity.ok(friendshipService.getAllFriend(vm.getSize(), vm.getCursor()));
    }

    @PostMapping("/accept-friend-request")
    public ResponseEntity<Void> acceptFriendRequest(@Valid @RequestBody FriendshipVM vm){
        friendshipService.acceptFriendRequest(vm.getFriendId());
        return ResponseEntity.ok(null);
    }
    @PostMapping("/remove-friend-request")
    public ResponseEntity<Void> removeFriendRequest(@Valid @RequestBody FriendshipVM vm){
        friendshipService.removeFriendRequest(vm.getFriendId());
        return ResponseEntity.ok(null);
    }

    @PostMapping("/remove-friend")
    public ResponseEntity<Void> removeFriend(@Valid @RequestBody FriendshipVM vm){
        friendshipService.removeFriend(vm.getFriendId());
        return ResponseEntity.ok(null);
    }
    /*
    * block user, can block if between users haven't relationship as ACCEPT, PENDING, BLOCK
    * */
    @PostMapping("/block-user")
    public ResponseEntity<Void> blockUser(@Valid @RequestBody FriendshipVM vm){
        friendshipService.blockUser(vm.getFriendId());
        return ResponseEntity.ok(null);
    }

    @PostMapping({"/block-user/remove", "/unblock-user"})
    public ResponseEntity<Void> removeBlockUser(@Valid @RequestBody FriendshipVM vm){
        friendshipService.removeBlock(vm.getFriendId());
        return ResponseEntity.ok(null);
    }

    @PostMapping("/blocked-users")
    public ResponseEntity<ResultPaginationCursorTime> getBlockedUsers(@RequestBody @Valid SizeAndCursorTimeVM vm) {
        return ResponseEntity.ok(friendshipService.getAllBlockedUsers(vm.getSize(), vm.getCursor()));
    }
}
