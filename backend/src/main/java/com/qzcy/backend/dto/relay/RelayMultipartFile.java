package com.qzcy.backend.dto.relay;

public record RelayMultipartFile(
        String fieldName,
        String filename,
        String contentType,
        byte[] content
) {
}
