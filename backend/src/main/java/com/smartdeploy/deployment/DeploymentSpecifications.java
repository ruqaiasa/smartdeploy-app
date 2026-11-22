package com.smartdeploy.deployment;

import org.springframework.data.jpa.domain.Specification;

public final class DeploymentSpecifications {

    private DeploymentSpecifications() {
    }

    public static Specification<Deployment> notDeleted() {
        return (root, query, builder) ->
                builder.isFalse(root.get("deleted"));
    }

    public static Specification<Deployment> hasStatus(
            DeploymentStatus status
    ) {
        return (root, query, builder) ->
                status == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("status"),
                        status
                );
    }

    public static Specification<Deployment> hasApplicationId(
            Long applicationId
    ) {
        return (root, query, builder) ->
                applicationId == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("version")
                                .get("application")
                                .get("id"),
                        applicationId
                );
    }

    public static Specification<Deployment> hasVersionId(
            Long versionId
    ) {
        return (root, query, builder) ->
                versionId == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("version").get("id"),
                        versionId
                );
    }

    /** RG-O1 : déploiements des applications d'un propriétaire (aucune restriction si null). */
    public static Specification<Deployment> ofApplicationsOwnedBy(Long ownerId) {
        return (root, query, builder) -> ownerId == null ? null
                : builder.equal(root.get("version").get("application").get("owner").get("id"), ownerId);
    }

    /** Portail développeur : les déploiements demandés par un utilisateur donné. */
    public static Specification<Deployment> requestedBy(Long userId) {
        return (root, query, builder) -> userId == null ? null
                : builder.equal(root.get("requestedBy").get("id"), userId);
    }

    public static Specification<Deployment> hasEnvironmentId(
            Long environmentId
    ) {
        return (root, query, builder) ->
                environmentId == null
                        ? builder.conjunction()
                        : builder.equal(
                        root.get("environment").get("id"),
                        environmentId
                );
    }

    public static Specification<Deployment> containsSearch(
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
                            builder.lower(
                                    root.get("version")
                                            .get("application")
                                            .get("name")
                            ),
                            value
                    ),
                    builder.like(
                            builder.lower(
                                    root.get("version").get("version")
                            ),
                            value
                    ),
                    builder.like(
                            builder.lower(
                                    root.get("environment").get("name")
                            ),
                            value
                    ),
                    builder.like(
                            builder.lower(
                                    root.get("requestedBy").get("email")
                            ),
                            value
                    )
            );
        };
    }
}