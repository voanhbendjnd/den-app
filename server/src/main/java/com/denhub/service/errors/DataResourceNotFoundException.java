package com.denhub.service.errors;

import com.denhub.web.rest.errors.ErrorConstants;
import com.denhub.web.rest.errors.NotFoundAlertException;

import java.io.Serial;

public class DataResourceNotFoundException extends NotFoundAlertException {
    @Serial
    private static final long serialVersionUID = 1L;

    public DataResourceNotFoundException(String message, String entityName, String errorKey){
        super(ErrorConstants.NOT_FOUND, message, entityName, errorKey);
    }

}
