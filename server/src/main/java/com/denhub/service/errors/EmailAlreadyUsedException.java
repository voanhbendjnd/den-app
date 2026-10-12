package com.denhub.service.errors;


import com.denhub.web.rest.errors.BadRequestAlertException;
import com.denhub.web.rest.errors.ErrorConstants;

import java.io.Serial;

public class EmailAlreadyUsedException extends BadRequestAlertException {
    @Serial
    private static final long serialVersionUID = 1L;

    public EmailAlreadyUsedException() {
        super(ErrorConstants.EMAIL_ALREADY_USED_TYPE, "Email address already in use", "userManagement", "emailexists");
    }
}