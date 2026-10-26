package com.hykon.iot_backend.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record DevicePageResponse(
        List<DeviceResponse> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static DevicePageResponse from(Page<DeviceResponse> page) {
        return new DevicePageResponse(
                List.copyOf(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}