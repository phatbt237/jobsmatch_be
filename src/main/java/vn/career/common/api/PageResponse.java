package vn.career.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** Standard paged payload. {@code page} is zero-based, like Spring Data. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
