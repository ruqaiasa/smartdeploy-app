package com.smartdeploy.incident;

import com.smartdeploy.audit.AuditAction;
import com.smartdeploy.audit.AuditRecordedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Lance l'analyse à chaque échec de pipeline et à chaque dégradation
 * détectée par le gardien, à partir du journal d'activité.
 */
@Component
public class IncidentAnalysisTrigger {

    private final IncidentAnalysisService analysisService;

    public IncidentAnalysisTrigger(IncidentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @EventListener
    public void onAuditRecorded(AuditRecordedEvent event) {
        var entry = event.entry();
        if (entry.getTargetId() == null) {
            return;
        }
        if (entry.getAction() == AuditAction.DEPLOYMENT_FAILED) {
            analysisService.analyzeInBackground(entry.getTargetId(), IncidentTrigger.PIPELINE);
        } else if (entry.getAction() == AuditAction.GUARD_DEGRADED) {
            analysisService.analyzeInBackground(entry.getTargetId(), IncidentTrigger.GUARD);
        }
    }
}
