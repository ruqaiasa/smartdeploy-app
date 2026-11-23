package com.smartdeploy.portal;

import com.smartdeploy.application.Application;
import com.smartdeploy.application.ApplicationAccess;
import com.smartdeploy.application.ApplicationRepository;
import com.smartdeploy.deployment.Deployment;
import com.smartdeploy.deployment.DeploymentKubernetesConfig;
import com.smartdeploy.deployment.DeploymentKubernetesConfigRepository;
import com.smartdeploy.deployment.DeploymentRepository;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.environment.Environment;
import com.smartdeploy.environment.EnvironmentRepository;
import com.smartdeploy.user.User;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Les applications en ligne de l'utilisateur : chaque environnement en service, sa version et son adresse. */
@RestController
public class OnlineApplicationsController {

    public record Online(Long environmentId, String applicationName, String environmentName, String environmentType,
                         String version, Long deploymentId, String url) {
    }

    private final ApplicationRepository applications;
    private final EnvironmentRepository environments;
    private final DeploymentRepository deployments;
    private final DeploymentKubernetesConfigRepository configs;

    public OnlineApplicationsController(ApplicationRepository applications, EnvironmentRepository environments,
                                        DeploymentRepository deployments, DeploymentKubernetesConfigRepository configs) {
        this.applications = applications;
        this.environments = environments;
        this.deployments = deployments;
        this.configs = configs;
    }

    @GetMapping("/api/portal/online")
    @PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN', 'VIEWER')")
    @Transactional(readOnly = true)
    public List<Online> online(@AuthenticationPrincipal User me) {
        List<Application> apps = ApplicationAccess.isRestricted(me)
                ? applications.findAllByOwner_IdAndDeletedFalseOrderByNameAsc(me.getId())
                : applications.findAll().stream().filter(a -> !a.isDeleted()).toList();
        List<Online> list = new ArrayList<>();
        for (Application app : apps) {
            for (Environment env : environments.findAllByApplication_IdAndDeletedFalse(app.getId())) {
                Deployment live = deployments.findTop10ByEnvironment_IdAndDeletedFalseOrderByCreatedAtDesc(env.getId()).stream()
                        .filter(d -> d.getStatus() == DeploymentStatus.SUCCESS)
                        .findFirst().orElse(null);
                if (live == null) {
                    continue;
                }
                String url = configs.findByDeploymentId(live.getId())
                        .map(DeploymentKubernetesConfig::getConfigurationJson)
                        .map(DeploymentProgressController::hostOf)
                        .orElse(null);
                list.add(new Online(env.getId(), app.getName(), env.getName(), env.getType() == null ? null : env.getType().name(),
                        live.getVersion().getVersion(), live.getId(), url));
            }
        }
        list.sort(Comparator.comparing(Online::applicationName).thenComparing(o -> order(o.environmentType())));
        return list;
    }

    private static int order(String type) {
        if (type == null) return 9;
        return switch (type) {
            case "DEVELOPMENT" -> 1;
            case "TEST" -> 2;
            case "STAGING" -> 3;
            case "PRODUCTION" -> 4;
            default -> 9;
        };
    }
}
