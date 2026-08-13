package com.smartdeploy.integration.jenkins;

import com.smartdeploy.deployment.DeploymentService;
import com.smartdeploy.deployment.DeploymentStatus;
import com.smartdeploy.deployment.UpdateDeploymentMetadataRequest;
import com.smartdeploy.deployment.UpdateDeploymentStatusRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/jenkins")
public class JenkinsWebhookController {

    private final DeploymentService deploymentService;

    public JenkinsWebhookController(
            DeploymentService deploymentService
    ) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    public void receiveWebhook(

            @RequestBody
            JenkinsWebhookRequest request

    ) {

        deploymentService.updateMetadata(

                request.deploymentId(),

                new UpdateDeploymentMetadataRequest(

                        request.buildNumber(),

                        request.logsUrl()

                )

        );

        deploymentService.updateStatus(

                request.deploymentId(),

                new UpdateDeploymentStatusRequest(

                        request.status(),

                        request.errorMessage()

                )

        );

    }

}