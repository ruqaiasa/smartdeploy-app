package com.smartdeploy.version;

import org.springframework.data.jpa.domain.Specification;

public final class VersionSpecifications {

    private VersionSpecifications() {
    }

    /** RG-O1 : versions des applications d'un propriétaire (aucune restriction si null). */
    public static Specification<Version> ownedBy(Long ownerId) {
        return (root, query, builder) -> ownerId == null ? null
                : builder.equal(root.get("application").get("owner").get("id"), ownerId);
    }

    public static Specification<Version> notDeleted() {
        return (root, query, builder) ->
                builder.isFalse(root.get("deleted"));
    }

    public static Specification<Version> containsSearch(
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
                            builder.lower(root.get("version")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("gitBranch")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("gitCommit")),
                            value
                    ),
                    builder.like(
                            builder.lower(root.get("dockerImage")),
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

    public static Specification<Version> hasBuildStatus(
            BuildStatus buildStatus
    ) {
        return (root, query, builder) ->
                buildStatus == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("buildStatus"),
                        buildStatus
                );
    }

    public static Specification<Version> hasApplicationId(
            Long applicationId
    ) {
        return (root, query, builder) ->
                applicationId == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("application").get("id"),
                        applicationId
                );
    }
}