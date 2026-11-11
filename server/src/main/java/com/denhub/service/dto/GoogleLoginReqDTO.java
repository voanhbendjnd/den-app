package com.denhub.service.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GoogleLoginReqDTO {
    String credential;
    String email;
    String name;
    String avatar;
    String googleId;
}
