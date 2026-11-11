package com.denhub.service.dto;


import java.io.Serial;
import java.io.Serializable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ResultPaginationDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    Meta meta;
    Object result;


    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class Meta {
        int page;
        int pageSize;
        int pages;
        long total;
    }
}