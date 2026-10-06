package tech.djnd.sample.app.service.errors;

import tech.djnd.sample.app.web.rest.errors.ErrorConstants;
import tech.djnd.sample.app.web.rest.errors.NotFoundAlertException;

import java.io.Serial;

public class DataResourceNotFoundException extends NotFoundAlertException {
    @Serial
    private static final long serialVersionUID = 1L;

    public DataResourceNotFoundException(String message, String entityName, String errorKey){
        super(ErrorConstants.NOT_FOUND, message, entityName, errorKey);
    }

}
