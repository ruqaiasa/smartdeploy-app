package com.smartdeploy.version;

import java.util.List;

public record VersionPageResponse(
        List<VersionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}