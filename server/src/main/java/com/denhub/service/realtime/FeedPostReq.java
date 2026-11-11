package com.denhub.service.realtime;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Length;

import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedPostReq {
    Long id;
    @NotBlank
    @Length(min = 1, max = 120)
    String content;
    String mediaUrl;
    @NotBlank
    String privacy;
    List<String> mediaUrls;




}
