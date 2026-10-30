package com.denhub.web.rest.vm;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SocialLoginVM {
    String accessToken;
    String code;
    @NotBlank
    String type;
}
