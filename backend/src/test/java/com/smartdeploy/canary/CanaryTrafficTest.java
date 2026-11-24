package com.smartdeploy.canary;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Déploiement progressif : la part du trafic reçue par le canary. */
class CanaryTrafficTest {

    @Test
    void theCanaryReceivesOnePodShareOfTheTraffic() {
        assertEquals(33, CanaryService.trafficPercent(2), "1 pod canary pour 2 pods stables");
        assertEquals(50, CanaryService.trafficPercent(1));
        assertEquals(25, CanaryService.trafficPercent(3));
        assertEquals(50, CanaryService.trafficPercent(0), "au moins un pod stable");
    }
}
