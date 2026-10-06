package tech.djnd.sample.app.service.errors;

import tech.djnd.sample.app.web.rest.errors.ErrorConstants;
import tech.djnd.sample.app.web.rest.errors.UnauthorizedAlertException;

import java.io.Serial;
/*
 * user not logged in 401
 * */
public class NotAuthorizedException extends UnauthorizedAlertException {
    @Serial
    private static final long  serialVersionUID = 1L;

    public NotAuthorizedException() {
        super(ErrorConstants.NOT_AUTHORIZED, "You are not logged in", "userManagement", "notloggedin");
    }
}