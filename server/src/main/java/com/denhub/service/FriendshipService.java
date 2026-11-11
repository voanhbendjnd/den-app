package com.denhub.service;

import com.denhub.domain.Friendship;
import com.denhub.domain.User;
import com.denhub.domain.enums.FriendshipStatus;
import com.denhub.repository.FriendshipRepository;
import com.denhub.repository.UserRepository;
import com.denhub.security.SecurityUtils;
import com.denhub.service.dto.ResultPaginationCursorTime;
import com.denhub.service.errors.AccessDeniedException;
import com.denhub.service.errors.BadRequestResourceException;
import com.denhub.service.errors.FriendReqAlreadySentException;
import com.denhub.service.errors.NotAuthorizedException;
import com.denhub.service.projection.FriendRequestProjection;
import com.denhub.service.realtime.FriendshipAction;
import com.denhub.service.realtime.NotificationFriendshipRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FriendshipService {
    FriendshipRepository friendshipRepository;
    UserRepository userRepository;
    public static final String SEND_FRIEND_REQUEST_RECEIVED = "SEND_FRIEND_REQUEST_RECEIVED";
    public static final String ACCEPT_FRIEND_REQUEST_RECEIVED = "ACCEPT_FRIEND_REQUEST_RECEIVED";
    NotificationFriendshipRequest notificationFriendshipRequest;
    public ResultPaginationCursorTime getAllFriendRequests(int size, String cursor){
        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        Instant parsedCursor = null;
        try{
            if(cursor != null && !cursor.isBlank()){
                parsedCursor = Instant.parse(cursor);
            }
        }
        catch(Exception e){
            throw new BadRequestResourceException("Parse cursor invalid", "time", "parseInvalid");
        }
        Pageable pageable =  PageRequest.of(0, size + 1);
        Slice<FriendRequestProjection> friendRequests = friendshipRepository.getAllFriendWithStatus(currentUserId,FriendshipStatus.PENDING.name() ,parsedCursor, pageable);
        List<FriendRequestProjection> content = friendRequests.getContent();
        boolean hasMore = content.size() > size;
        if(hasMore){
            content.removeLast();
        }
        String nextCursor = null;
        if(!content.isEmpty()){
            nextCursor = content.getLast().getLastModifiedDate().toString();

        }
        ResultPaginationCursorTime result = new ResultPaginationCursorTime();
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        result.setResult(content);
        return result;


    }

    public void removeBlock(Long userRemoveBlockId){
        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        if(currentUserId.equals(userRemoveBlockId)){
            throw new BadRequestResourceException("Cannot remove block my self", "friendshipManagement", "cannotremoveblock");
        }
        if(!userRepository.existsById(userRemoveBlockId)){
            throw new BadRequestResourceException("User not found", "userManagement", "idnotfound");
        }
        Long userOneId = Math.min(currentUserId, userRemoveBlockId);
        Long userTwoId = Math.max(currentUserId, userRemoveBlockId);
        Friendship currentFs = friendshipRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId).orElseThrow(() ->
                new BadRequestResourceException("Friendship not found", "friendshipmanagement", "friendshipnotfound"));
        if(!currentFs.getStatus().equals(FriendshipStatus.BLOCKED.name())){
            throw new BadRequestResourceException("Friendship status not blocked", "friendshipmanagement", "friendshipnotblocked");
        }
        if(!currentFs.getActionUserId().equals(currentUserId)){
            throw new BadRequestResourceException("You cannot remove block for this user", "friendshipManagement", "cannotremoveblock");
        }
        friendshipRepository.delete(currentFs);
    }

    public void blockUser(Long blockUserId){
        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        if(blockUserId.equals(currentUserId)){
            throw new BadRequestResourceException("Cannot block my self", "friendshipManagement", "cannotblock");
        }
        if(!userRepository.existsById(blockUserId)){
            throw new BadRequestResourceException("User not found", "userManagement", "idnotfound");
        }

        Long userOneId = Math.min(blockUserId, currentUserId);
        Long userTwoId = Math.max(blockUserId, currentUserId);
        Optional<Friendship> currentFsOps = friendshipRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId);
        if(currentFsOps.isPresent()){
            Friendship currentFs = currentFsOps.get();
            String currentStatus = currentFs.getStatus();
            if(FriendshipStatus.BLOCKED.name().equals(currentStatus)){
                throw new BadRequestResourceException("This user already blocked", "friendshipManagement", "cannotblock");
            }
            currentFs.setStatus(FriendshipStatus.BLOCKED.name());
            currentFs.setActionUserId(currentUserId);
            friendshipRepository.save(currentFs);
        } else {
            Friendship newFs = new Friendship();
            newFs.setUserOneId(userOneId);
            newFs.setUserTwoId(userTwoId);
            newFs.setStatus(FriendshipStatus.BLOCKED.name());
            newFs.setActionUserId(currentUserId);
            friendshipRepository.save(newFs);
        }
    }

    public ResultPaginationCursorTime getAllBlockedUsers(int size, String cursor){
        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        Instant parsedCursor = null;
        try{
            if(cursor != null && !cursor.isBlank()){
                parsedCursor = Instant.parse(cursor);
            }
        }
        catch(Exception e){
            throw new BadRequestResourceException("Parse cursor invalid", "time", "parseInvalid");
        }
        Pageable pageable = PageRequest.of(0, size + 1);
        Slice<FriendRequestProjection> blockedUsers = friendshipRepository.getAllFriendWithStatus(currentUserId, FriendshipStatus.BLOCKED.name(), parsedCursor, pageable);
        List<FriendRequestProjection> content = blockedUsers.getContent();
        boolean hasMore = content.size() > size;
        if(hasMore){
            content.removeLast();
        }
        String nextCursor = null;
        if(!content.isEmpty()){
            nextCursor = content.getLast().getLastModifiedDate().toString();
        }
        ResultPaginationCursorTime result = new ResultPaginationCursorTime();
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        result.setResult(content);
        return result;
    }

    public ResultPaginationCursorTime getAllFriend(int size, String cursor){
        Long currentUserId = SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        Instant parsedCursor = null;
        try{
            if(cursor != null && !cursor.isBlank()){
                parsedCursor = Instant.parse(cursor);
            }
        }
        catch(Exception e){
            throw new BadRequestResourceException("Parse cursor invalid", "time", "parseInvalid");
        }
        Pageable pageable =  PageRequest.of(0, size + 1);
        Slice<FriendRequestProjection> friendRequests = friendshipRepository.getAllFriendWithStatus(currentUserId,FriendshipStatus.ACCEPT.name() ,parsedCursor, pageable);
        List<FriendRequestProjection> content = friendRequests.getContent();
        boolean hasMore = content.size() > size;
        if(hasMore){
            content.removeLast();
        }
        String nextCursor = null;
        if(!content.isEmpty()){
            nextCursor = content.getLast().getLastModifiedDate().toString();

        }
        ResultPaginationCursorTime result = new ResultPaginationCursorTime();
        result.setNextCursor(nextCursor);
        result.setHasMore(hasMore);
        result.setResult(content);
        return result;


    }
    /*
     * sender or receiver use
     * - with sender remove request sent
     * - with receiver remove request sender sent
     * */
    public void removeFriendRequest(Long friendTargetRemoveId){
        if(!userRepository.existsById(friendTargetRemoveId)){
            throw new BadRequestResourceException("User not found", "userManagement", "idnotfound");
        }
        Long currentUserId =  SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        if(currentUserId.equals(friendTargetRemoveId)){
            throw new BadRequestResourceException("Id current and friend is same", "friendshipManagement", "sameid");
        }
        Long userOneId = Math.min(friendTargetRemoveId, currentUserId);
        Long userTwoId = Math.max(friendTargetRemoveId, currentUserId);
        Optional<Friendship> currentFsOps = friendshipRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId);
        if(currentFsOps.isEmpty()){
            throw new BadRequestResourceException("Cannot found friend relationship", "friendshipManagement", "idnotfound");
        }
        Friendship currentFs = currentFsOps.get();
        String currentStatusFs = currentFs.getStatus();
        if (!currentStatusFs.equals(FriendshipStatus.PENDING.name())) {
            throw new BadRequestResourceException("Cannot remove friend request without status PENDING", "friendManagement", "cannotremoverequest");
        }
        friendshipRepository.delete(currentFs);


    }
    public void removeFriend(Long friendTargetRemoveId){
        if(!userRepository.existsById(friendTargetRemoveId)){
            throw new BadRequestResourceException("User not found", "userManagement", "idnotfound");
        }
        Long currentUserId =  SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        if(currentUserId.equals(friendTargetRemoveId)){
            throw new BadRequestResourceException("Id current and friend is same", "friendshipManagement", "sameid");
        }
        Long userOneId = Math.min(friendTargetRemoveId, currentUserId);
        Long userTwoId = Math.max(friendTargetRemoveId, currentUserId);
        Optional<Friendship> currentFsOps = friendshipRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId);
        if(currentFsOps.isEmpty()){
            throw new BadRequestResourceException("Cannot found friend relationship", "friendshipManagement", "idnotfound");
        }
        Friendship currentFs = currentFsOps.get();
        String currentStatusFs = currentFs.getStatus();
        if (!currentStatusFs.equals(FriendshipStatus.ACCEPT.name())) {
            throw new BadRequestResourceException("Cannot remove friend without status ACCEPT", "friendManagement", "cannotremovefriend");
        }
        friendshipRepository.delete(currentFs);


    }

    public void acceptFriendRequest(Long friendTargetAcceptId){
        if(!userRepository.existsById(friendTargetAcceptId)){
            throw new BadRequestResourceException("User not found", "userManagement", "idnotfound");
        }
        Long currentUserId =  SecurityUtils.getCurrentUserIdAtHolder();
        if(currentUserId == null){
            throw new AccessDeniedException("You are not logged in");
        }
        if(currentUserId.equals(friendTargetAcceptId)){
            throw new BadRequestResourceException("Id current and friend is same", "friendshipManagement", "sameid");
        }
        Long userOneId = Math.min(friendTargetAcceptId, currentUserId);
        Long userTwoId = Math.max(friendTargetAcceptId, currentUserId);
        Optional<Friendship> currentFsOps = friendshipRepository.findByUserOneIdAndUserTwoId(userOneId, userTwoId);
        if(currentFsOps.isPresent()){
            Friendship currentFs = currentFsOps.get();
            String currentStatusFs = currentFs.getStatus();
            if (currentFs.getActionUserId().equals(currentUserId)) {
                throw new BadRequestResourceException("Cannot accept your own friend request", "friendManagement", "cannotAcceptOwnRequest");
            }

            if (!currentStatusFs.equals(FriendshipStatus.PENDING.name())) {
                throw new BadRequestResourceException("Cannot cancel friend request without status PENDING", "friendManagement", "cannotCancelRequest");
            }
            currentFs.setStatus(FriendshipStatus.ACCEPT.name());
            currentFs.setActionUserId(currentUserId);
            friendshipRepository.save(currentFs);
        }
        else{
            throw new BadRequestResourceException("Cannot found friend relationship", "friendshipManagement", "idnotfound");

        }


    }




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
                    .event(SEND_FRIEND_REQUEST_RECEIVED)
                    .senderId(currentUserId)
                    .username(sender.getUsername())
                    .avatar(sender.getAvatar() != null ? sender.getAvatar() : "")
                    .name(sender.getName())
                    .lastModifiedDate(newFs.getLastModifiedDate().toString())
                    .status(newFs.getStatus())
                    .build(), friendTargetId);
        }

    }



}
