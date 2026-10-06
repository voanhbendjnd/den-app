package tech.djnd.sample.app.web.rest.vm;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tech.djnd.sample.app.service.dto.UserDTO;

@Getter
@Setter
public class ManagedUserVM extends UserDTO {
    public static final int PASSWORD_MIN_LENGTH = 4;

    public static final int PASSWORD_MAX_LENGTH = 100;

    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH)
    private String password;
}