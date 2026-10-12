package com.denhub.service.errors;


import com.denhub.web.rest.errors.ErrorConstants;
import com.denhub.web.rest.errors.UnauthorizedAlertException;

import java.io.Serial;

public class SessionInvalidException extends UnauthorizedAlertException {
    @Serial
    private static final long serialVersionUID = 1L;
    public SessionInvalidException() {
        super(ErrorConstants.INVALID_PASSWORD_TYPE, "Session invalid", "authManagement", "invalidsession");
    }
}