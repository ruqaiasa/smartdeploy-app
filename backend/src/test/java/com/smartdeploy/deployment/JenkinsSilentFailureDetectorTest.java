package com.smartdeploy.deployment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Échecs silencieux de Jenkins : lire la file d'attente et la raison de l'échec. */
class JenkinsSilentFailureDetectorTest {

    @Test
    void theQueueNumberIsReadFromTheQueueAddress() {
        assertEquals(4512L, JenkinsSilentFailureDetector.queueId("http://192.168.85.129:8080/queue/item/4512/"));
        assertNull(JenkinsSilentFailureDetector.queueId(null));
        assertNull(JenkinsSilentFailureDetector.queueId("http://jenkins/job/smartdeploy/"));
    }

    @Test
    void theReasonIsTheFirstMeaningfulErrorOfTheConsole() {
        // Cas réel : un Jenkinsfile illisible, Jenkins s'arrête avant sa première étape.
        String console = String.join("\n",
                "Started by user admin",
                "Obtained jenkins/Jenkinsfile from git https://github.com/ruqaiasa/smartdeploy-app.git",
                "org.codehaus.groovy.control.MultipleCompilationErrorsException: startup failed:",
                "WorkflowScript: 1074: unexpected char: '\\' @ line 1074, column 48.",
                "\tat org.codehaus.groovy.control.ErrorCollector.failIfErrors(ErrorCollector.java:309)",
                "Finished: FAILURE");
        assertTrue(JenkinsSilentFailureDetector.firstError(console).contains("startup failed"));
        assertNull(JenkinsSilentFailureDetector.firstError("Started by user admin\nFinished: FAILURE"));
    }
}
