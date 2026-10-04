package tech.djnd.sample.app.service.dto;


import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.StringUtils;
import tech.djnd.sample.app.config.Constants;
import tech.djnd.sample.app.domain.Authority;
import tech.djnd.sample.app.domain.User;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class UserDTO {

    Long id;
    String name;
    @Email
    @NotBlank(message = "User email not found")
    String email;
    String createdBy;
    Instant createdDate;
    String lastModifiedBy;
    Instant lastModifiedDate;
    boolean activated;
    Set<String> authorities;

    public UserDTO() {
    }

    public UserDTO(User user) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.activated = user.getActivated();
        this.createdBy = user.getCreatedBy();
        this.createdDate = user.getCreatedDate();
        this.lastModifiedBy = user.getLastModifiedBy();
        this.lastModifiedDate = user.getLastModifiedDate();
        this.authorities = user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet());
    }

}
