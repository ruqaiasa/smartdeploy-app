package com.smartdeploy.user;

import org.springframework.data.jpa.domain.Specification;

public final class UserSpecifications {

    private UserSpecifications() {
    }

    public static Specification<User> notDeleted() {
        return (root, query, builder) ->
                builder.isFalse(root.get("deleted"));
    }

    public static Specification<User> hasRole(Role role) {
        return (root, query, builder) ->
                role == null
                        ? builder.conjunction()
                        : builder.equal(root.get("role"), role);
    }

    public static Specification<User> hasEnabled(Boolean enabled) {
        return (root, query, builder) ->
                enabled == null
                        ? builder.conjunction()
                        : builder.equal(root.get("enabled"), enabled);
    }

    public static Specification<User> containsSearch(String search) {
        return (root, query, builder) -> {

            if (search == null || search.isBlank()) {
                return builder.conjunction();
            }

            String value = "%" + search.trim().toLowerCase() + "%";

            return builder.or(
                    builder.like(
                            builder.lower(root.get("firstName")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("lastName")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("email")),
                            value
                    )
            );
        };
    }
}