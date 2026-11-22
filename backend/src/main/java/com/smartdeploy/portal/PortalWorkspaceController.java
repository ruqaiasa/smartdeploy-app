package com.smartdeploy.portal;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.user.User;
import com.smartdeploy.version.Version;
import com.smartdeploy.version.VersionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * « Mon espace » : en une seule requête, les applications de l'utilisateur connecté, l'état de chacun
 * de leurs environnements (version en service, dernier déploiement) et leurs dernières versions.
 */
@RestController
public class PortalWorkspaceController {

    public record EnvironmentState(Long id, String name, String type, String currentVersion, LocalDateTime currentSince,
                                   Long lastDeploymentId, String lastStatus, String lastVersion, LocalDateTime lastAt) {
    }

    public record VersionSummary(Long id, String version, String branch, boolean pinned) {
    }

    public record WorkspaceApplication(Long id, String name, String description, String repositoryUrl, String technology,
                                       boolean active, List<EnvironmentState> environments, List<VersionSummary> latestVersions) {
    }

    public record Workspace(int applications, int inService, int pendingRequests, List<WorkspaceApplication> items) {
    }

    private final ApplicationRepository applications;
    private final EnvironmentRepository environments;
    private final VersionRepository versions;
    private final DeploymentRepository deployments;

    public PortalWorkspaceController(ApplicationRepository applications, EnvironmentRepository environments,
                                     VersionRepository versions, DeploymentRepository deployments) {
        this.applications = applications;
        this.environments = environments;
        this.versions = versions;
        this.deployments = deployments;
    }

    @GetMapping("/api/portal/workspace")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public Workspace workspace(@AuthenticationPrincipal User user) {
        List<WorkspaceApplication> items = applications.findAllByOwner_IdAndDeletedFalseOrderByNameAsc(user.getId()).stream()
                .map(this::toItem)
                .toList();
        int inService = (int) items.stream().flatMap(a -> a.environments().stream()).filter(e -> e.currentVersion() != null).count();
        int pending = (int) deployments.findByStatusInAndDeletedFalse(List.of(DeploymentStatus.AWAITING_APPROVAL)).stream()
                .filter(d -> d.getRequestedBy() != null && Objects.equals(d.getRequestedBy().getId(), user.getId()))
                .count();
        return new Workspace(items.size(), inService, pending, items);
    }

    private WorkspaceApplication toItem(Application app) {
        List<EnvironmentState> envs = environments.findAllByApplication_IdAndDeletedFalse(app.getId()).stream()
                .sorted(Comparator.comparing(e -> e.getType() == null ? 99 : e.getType().ordinal()))
                .map(this::toState)
                .toList();
        List<VersionSummary> latest = versions.findAllByApplicationAndDeletedFalse(app).stream()
                .sorted(Comparator.comparing(Version::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(v -> new VersionSummary(v.getId(), v.getVersion(), v.getGitBranch(), v.getPinnedAt() != null))
                .toList();
        return new WorkspaceApplication(app.getId(), app.getName(), app.getDescription(), app.getRepositoryUrl(),
                app.getTechnology() == null ? null : app.getTechnology().name(), app.isActive(), envs, latest);
    }

    private EnvironmentState toState(Environment env) {
        Optional<Deployment> current = deployments.findFirstByEnvironmentAndStatusAndDeletedFalseOrderByCreatedAtDesc(env, DeploymentStatus.SUCCESS);
        Optional<Deployment> last = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(env.getId()).stream().findFirst();
        return new EnvironmentState(env.getId(), env.getName(), env.getType() == null ? null : env.getType().name(),
                current.map(d -> d.getVersion().getVersion()).orElse(null),
                current.map(d -> d.getFinishedAt() != null ? d.getFinishedAt() : d.getCreatedAt()).orElse(null),
                last.map(Deployment::getId).orElse(null), last.map(d -> d.getStatus().name()).orElse(null),
                last.map(d -> d.getVersion().getVersion()).orElse(null), last.map(Deployment::getCreatedAt).orElse(null));
    }
}
