package com.denhub.service;

import com.denhub.domain.Friendship;
import com.denhub.domain.User;
import com.denhub.domain.enums.FriendshipStatus;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.FriendshipDTO;
import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.service.errors.FriendReqAlreadySentException;
import com.denhub.service.errors.NotAuthorizedException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendshipService {
    FriendshipRepository friendshipRepository;
    UserRepository userRepository;

    public void sendRequestAddFriend(Long friendTargetId){
        User friend = userRepository.findById(friendTargetId).orElseThrow(() -> new BadRequestResourceException("User not found", "userManagement", "idnotfound"));
        Long currentUserId = SecurityUtils.getCurrentUserIdOrNull();
        if(currentUserId == null){
            throw new NotAuthorizedException();
        }
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
                    return;
                }
                else{
                    throw new FriendReqAlreadySentException();
                }
            }
        }

        else{
            Friendship newFs = new  Friendship();

            newFs.setUserOneId(userOneId);
            newFs.setUserTwoId(userTwoId);
            newFs.setStatus(FriendshipStatus.PENDING.name());
            newFs.setActionUserId(currentUserId);
            friendshipRepository.save(newFs);
            // implement socket
        }

    }



}
