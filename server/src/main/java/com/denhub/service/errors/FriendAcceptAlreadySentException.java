package com.denhub.service.errors;

import com.denhub.web.rest.errors.BadRequestAlertException;
import com.denhub.web.rest.errors.ErrorConstants;

import java.io.Serial;

public class FriendAcceptAlreadySentException extends BadRequestAlertException {
    @Serial
    private static final long serialVersionUID = 1L;

    public FriendAcceptAlreadySentException() {
        super(ErrorConstants.FRIEND_ACCEPT_ALREADY, "Friend already accept", "friendshipManagement", "friendacceptalready");
    }
}
