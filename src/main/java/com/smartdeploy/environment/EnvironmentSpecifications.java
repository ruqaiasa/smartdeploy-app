package com.smartdeploy.environment;

import org.springframework.data.jpa.domain.Specification;

public final class EnvironmentSpecifications {

    private EnvironmentSpecifications() {
    }

    public static Specification<Environment> notDeleted() {

        return (root, query, builder) ->
                builder.isFalse(root.get("deleted"));
    }

    public static Specification<Environment> containsSearch(
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
                            builder.lower(root.get("namespace")),
                            value
                    ),

                    builder.like(
                            builder.lower(
                                    root.get("application")
                                            .get("name")
                            ),
                            value
                    )
            );
        };
    }

    public static Specification<Environment> hasType(
            EnvironmentType type
    ) {

        return (root, query, builder) ->

                type == null

                        ? builder.conjunction()

                        : builder.equal(
                        root.get("type"),
                        type
                );
    }

    public static Specification<Environment> hasActive(
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