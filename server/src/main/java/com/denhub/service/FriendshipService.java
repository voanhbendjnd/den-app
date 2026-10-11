package com.denhub.service;

import com.denhub.domain.Friendship;
import com.denhub.domain.User;
import com.denhub.domain.enums.FriendshipStatus;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.service.errors.FriendReqAlreadySentException;
import com.denhub.service.errors.NotAuthorizedException;
import com.denhub.service.realtime.FriendshipAction;
import com.denhub.service.realtime.NotificationFriendshipRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendshipService {
    FriendshipRepository friendshipRepository;
    UserRepository userRepository;
    NotificationFriendshipRequest notificationFriendshipRequest;

    public void sendRequestAddFriend(Long friendTargetId){
        User friend = userRepository.findById(friendTargetId).orElseThrow(() -> new BadRequestResourceException("User not found", "userManagement", "idnotfound"));

        Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();
        if(currentUserId == null){
            throw new NotAuthorizedException();
        }
        User sender = userRepository.findById(currentUserId).orElseThrow(() -> new BadRequestResourceException("User not found", "userManagement", "idnotfound"));

        if(friend.getId().equals(currentUserId)){
            throw new BadRequestResourceException("You cannot send request for self", "friendManagement", "idduplicate");
        }

        Long userOneId = Math.min(currentUserId, friendTargetId);
        Long userTwoId = Math.max(currentUserId, friendTargetId);
        Optional<Friendship> currentFsOps = friendshipRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId);
        if(currentFsOps.isPresent()) {
            Friendship currentFs = currentFsOps.get();
            String currentStatusFs = currentFs.getStatus();
            if (currentStatusFs.equals(FriendshipStatus.BLOCKED.name())) {
                throw new BadRequestResourceException("Cannot send friend request to this user", "friendManagement", "cannotSendRequest");
            }
            if (currentStatusFs.equals(FriendshipStatus.ACCEPT.name())) {
                throw new BadRequestResourceException("You are already friends with this user", "friendManagement", "alreadyFriends");
            }
            if (currentStatusFs.equals(FriendshipStatus.PENDING.name())) {
                if(!currentFs.getActionUserId().equals(currentUserId)){
                    currentFs.setStatus(FriendshipStatus.ACCEPT.name());
                    friendshipRepository.save(currentFs);
                    // implement socket
                    // accept and send request accept for friend
                    notificationFriendshipRequest.requestAccepted(FriendshipAction.builder()
                                    .senderId(currentUserId)
                                    .avatar(sender.getAvatar())
                                    .name(sender.getName())
                                    .username(sender.getUsername())
                            .build(), friendTargetId);
                }
                else{
                    throw new FriendReqAlreadySentException();
                }
            }
        }

        else{
            Friendship newFs = new Friendship();

            newFs.setUserOneId(userOneId);
            newFs.setUserTwoId(userTwoId);
            newFs.setStatus(FriendshipStatus.PENDING.name());
            newFs.setActionUserId(currentUserId);
            friendshipRepository.save(newFs);
            // socket
            notificationFriendshipRequest.requestReceived(FriendshipAction.builder()
                            .senderId(currentUserId)
                            .username(sender.getUsername())
                            .avatar(sender.getAvatar() != null ? sender.getAvatar() : "")
                            .name(sender.getName())
                    .build(), friendTargetId);
        }

    }



}
