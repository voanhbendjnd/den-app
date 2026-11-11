package com.denhub.service.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

public class ResultPaginationCursorDateId extends ResultPaginationDTO{
    boolean hasMore;
    String cursorDate;
    Long cursorId;
}
