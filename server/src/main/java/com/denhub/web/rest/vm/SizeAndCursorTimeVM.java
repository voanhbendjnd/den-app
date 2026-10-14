package com.denhub.web.rest.vm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SizeAndCursorTimeVM {
    @Min(value = 0)
    @Max(value = 22)
    int size;
    String cursor;
}
