package com.smartdeploy.guard;

import org.springframework.beans.factory.annotation.Value;
import com.smartdeploy.environment.EnvironmentType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/** Réglages du gardien (application.yml, section smartdeploy.guard). */
@Component
public class GuardProperties {

    private final boolean enabled;
    private final int windowMinutes;
    private final int baselineMinutes;
    private final int criticalConfirmations;
    private final Set<EnvironmentType> autoRollbackEnvironments;

    @org.springframework.beans.factory.annotation.Autowired
    public GuardProperties(
            @Value("${smartdeploy.guard.enabled:true}") boolean enabled,
            @Value("${smartdeploy.guard.window-minutes:10}") int windowMinutes,
            @Value("${smartdeploy.guard.baseline-minutes:15}") int baselineMinutes,
            @Value("${smartdeploy.guard.critical-confirmations:2}") int criticalConfirmations,
            @Value("${smartdeploy.guard.auto-rollback-environments:DEVELOPMENT,TEST}") String autoRollbackEnvironments
    ) {
        this.enabled = enabled;
        this.windowMinutes = windowMinutes;
        this.baselineMinutes = baselineMinutes;
        this.criticalConfirmations = Math.max(1, criticalConfirmations);
        this.autoRollbackEnvironments = parse(autoRollbackEnvironments);
    }

    /** Compatibilité : sans retour arrière automatique. */
    public GuardProperties(boolean enabled, int windowMinutes, int baselineMinutes, int criticalConfirmations) {
        this(enabled, windowMinutes, baselineMinutes, criticalConfirmations, "");
    }

    static Set<EnvironmentType> parse(String value) {
        Set<EnvironmentType> types = EnumSet.noneOf(EnvironmentType.class);
        if (value == null) {
            return types;
        }
        for (String item : value.split(",")) {
            try {
                if (!item.isBlank()) {
                    types.add(EnvironmentType.valueOf(item.trim()));
                }
            } catch (IllegalArgumentException ignored) {
                // Type inconnu : ignoré.
            }
        }
        return types;
    }

    /** Environnements où le gardien revient seul à la dernière version saine. */
    public boolean autoRollback(EnvironmentType type) {
        return type != null && autoRollbackEnvironments.contains(type);
    }

    public boolean enabled() { return enabled; }
    public int windowMinutes() { return windowMinutes; }
    public int baselineMinutes() { return baselineMinutes; }
    public int criticalConfirmations() { return criticalConfirmations; }
}
