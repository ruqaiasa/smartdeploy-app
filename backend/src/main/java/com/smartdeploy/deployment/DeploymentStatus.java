package com.smartdeploy.deployment;

public enum DeploymentStatus {

    PENDING,

    RUNNING,

    SUCCESS,

    FAILED,

    CANCELLED,

    ROLLED_BACK,

    // Approbation avant la production (principe des quatre yeux)
    AWAITING_APPROVAL,

    REJECTED

}