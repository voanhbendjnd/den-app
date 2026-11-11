package com.denhub.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Dữ liệu phản hồi phân trang bằng con trỏ (Cursor Pagination)")
public class CursorPageResponse<T> {

    @Schema(description = "Danh sách phần tử trong trang hiện tại")
    List<T> items;

    @Schema(description = "ID con trỏ cho trang tiếp theo (null nếu không còn trang tiếp theo)", example = "104")
    Long nextCursor;

    @Schema(description = "Còn dữ liệu ở trang tiếp theo hay không", example = "true")
    boolean hasMore;

    @Schema(description = "Số lượng phần tử tối đa yêu cầu mỗi trang", example = "30")
    int limit;

    // Backward-compatible record-like accessors
    public List<T> items() {
        return this.items;
    }

    public Long nextCursor() {
        return this.nextCursor;
    }

    public boolean hasMore() {
        return this.hasMore;
    }

    public int limit() {
        return this.limit;
    }
}
