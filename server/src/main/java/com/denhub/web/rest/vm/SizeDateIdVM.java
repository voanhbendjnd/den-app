package com.denhub.web.rest.vm;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SizeDateIdVM {
    @Min(value = 0)
    @Max(value = 22)
    int size;
    String cursorDate;
    Long cursorId;
}
