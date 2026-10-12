package com.denhub.service.errors;

import com.denhub.web.rest.errors.BadRequestAlertException;
import com.denhub.web.rest.errors.ErrorConstants;

import java.io.Serial;

public class FriendBlockedAlreadyException extends BadRequestAlertException {
    @Serial
    private static final long serialVersionUID = 1L;

    public FriendBlockedAlreadyException() {
        super(ErrorConstants.FRIEND_BLOCKED_ALREADY, "Friend already blocked", "friendshipManagement", "blockfriend");
    }
}
