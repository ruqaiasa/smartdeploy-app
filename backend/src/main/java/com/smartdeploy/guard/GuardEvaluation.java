package com.smartdeploy.guard;

import java.util.List;

/** Ensemble des contrôles d'une évaluation et leur niveau global. */
public record GuardEvaluation(GuardLevel level, List<GuardCheck> checks) {

    public List<GuardCheck> critical() {
        return checks.stream().filter(check -> check.level() == GuardLevel.CRITICAL).toList();
    }

    public List<GuardCheck> warnings() {
        return checks.stream().filter(check -> check.level() == GuardLevel.WARNING).toList();
    }
}
