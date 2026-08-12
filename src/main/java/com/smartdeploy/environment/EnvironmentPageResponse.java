package com.smartdeploy.environment;

import java.util.List;

public record EnvironmentPageResponse(

        List<EnvironmentResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last

) {
}