package com.denhub.service.errors;

import com.denhub.web.rest.errors.BadRequestAlertException;
import com.denhub.web.rest.errors.ErrorConstants;

import java.io.Serial;

public class FriendReqAlreadySentException extends BadRequestAlertException {
    @Serial
    private static final long serialVersionUID = 1L;
    public FriendReqAlreadySentException() {
        super(ErrorConstants.FRIEND_REQUEST_ALREADY_SENT, "You are already send request", "friendshipManagement", "friendalreadysent");
    }
}
