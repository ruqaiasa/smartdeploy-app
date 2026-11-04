package com.smartdeploy.guard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Réglages du gardien (application.yml, section smartdeploy.guard). */
@Component
public class GuardProperties {

    private final boolean enabled;
    private final int windowMinutes;
    private final int baselineMinutes;
    private final int criticalConfirmations;

    public GuardProperties(
            @Value("${smartdeploy.guard.enabled:true}") boolean enabled,
            @Value("${smartdeploy.guard.window-minutes:10}") int windowMinutes,
            @Value("${smartdeploy.guard.baseline-minutes:15}") int baselineMinutes,
            @Value("${smartdeploy.guard.critical-confirmations:2}") int criticalConfirmations
    ) {
        this.enabled = enabled;
        this.windowMinutes = windowMinutes;
        this.baselineMinutes = baselineMinutes;
        this.criticalConfirmations = Math.max(1, criticalConfirmations);
    }

    public boolean enabled() { return enabled; }
    public int windowMinutes() { return windowMinutes; }
    public int baselineMinutes() { return baselineMinutes; }
    public int criticalConfirmations() { return criticalConfirmations; }
}
