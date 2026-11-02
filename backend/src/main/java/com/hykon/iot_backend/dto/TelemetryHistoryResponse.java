package com.hykon.iot_backend.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record TelemetryHistoryResponse(
        List<TelemetryReadingResponse> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static TelemetryHistoryResponse from(
            Page<TelemetryReadingResponse> page
    ) {
        return new TelemetryHistoryResponse(
                List.copyOf(page.getContent()),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}