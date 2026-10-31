package com.smartdeploy.integration.jenkins;

import com.smartdeploy.deployment.DeploymentService;
import com.smartdeploy.deployment.PipelineCallbackResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * Callbacks du pipeline Jenkins.
 *
 * Accès réservé au rôle JENKINS (en-tête X-SmartDeploy-Api-Key,
 * voir JenkinsApiKeyFilter et SecurityConfig).
 */
@RestController
@RequestMapping("/api/webhooks/jenkins")
public class JenkinsWebhookController {

    private static final Logger log =
            LoggerFactory.getLogger(JenkinsWebhookController.class);

    private final DeploymentService deploymentService;

    public JenkinsWebhookController(
            DeploymentService deploymentService
    ) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    public PipelineCallbackResult receiveWebhook(
            @RequestBody JenkinsWebhookRequest request
    ) {
        PipelineCallbackResult result =
                deploymentService.applyPipelineCallback(
                        request.deploymentId(),
                        request.status(),
                        request.buildNumber(),
                        request.logsUrl(),
                        request.errorMessage(),
                        request.kubernetesDeploymentName()
                );

        log.info(
                "Callback Jenkins : déploiement {}, build #{}, statut reçu {} → {} ({})",
                request.deploymentId(),
                request.buildNumber(),
                request.status(),
                result.status(),
                result.outcome()
        );

        return result;
    }
}
