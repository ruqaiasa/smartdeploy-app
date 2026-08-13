package com.smartdeploy.application;

import org.springframework.data.jpa.domain.Specification;

public final class ApplicationSpecifications {

    private ApplicationSpecifications() {
    }

    public static Specification<Application> notDeleted() {
        return (root, query, builder) ->
                builder.isFalse(root.get("deleted"));
    }

    public static Specification<Application> containsSearch(
            String search
    ) {
        return (root, query, builder) -> {

            if (search == null || search.isBlank()) {
                return builder.conjunction();
            }

            String value =
                    "%" + search.trim().toLowerCase() + "%";

            return builder.or(
                    builder.like(
                            builder.lower(root.get("name")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("description")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("repositoryUrl")),
                            value
                    )
            );
        };
    }

    public static Specification<Application> hasTechnology(
            Technology technology
    ) {
        return (root, query, builder) ->
                technology == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("technology"),
                        technology
                );
    }

    public static Specification<Application> hasActive(
            Boolean active
    ) {
        return (root, query, builder) ->
                active == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("active"),
                        active
                );
    }
}