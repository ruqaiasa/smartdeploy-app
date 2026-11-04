package com.smartdeploy.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Vérifie la chaîne d'empreintes du journal, entrée par entrée :
 * - l'empreinte recalculée doit être égale à l'empreinte enregistrée
 *   (sinon : entrée modifiée) ;
 * - previousHash doit être l'empreinte de l'entrée précédente
 *   (sinon : entrée supprimée ou insérée).
 * Limite connue : la suppression des dernières entrées n'est pas visible
 * dans la chaîne seule ; elle se détecte par un export régulier.
 */
@Service
@Transactional(readOnly = true)
public class AuditIntegrityService {

    private static final int BATCH_SIZE = 500;

    private final AuditLogRepository auditLogRepository;

    public AuditIntegrityService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditIntegrityReport verify() {
        long checked = 0;
        long unchained = 0;
        String expectedPrevious = null;
        boolean chainStarted = false;
        int pageIndex = 0;

        Page<AuditLog> page;
        do {
            page = auditLogRepository.findAll(PageRequest.of(pageIndex++, BATCH_SIZE, Sort.by("id")));
            for (AuditLog entry : page.getContent()) {
                if (entry.getHash() == null) {
                    if (chainStarted) {
                        return broken(checked, unchained, entry, "Entrée sans empreinte au milieu de la chaîne");
                    }
                    unchained++;
                    continue;
                }
                if (!chainStarted) {
                    chainStarted = true;
                    expectedPrevious = AuditChain.GENESIS;
                }
                if (!expectedPrevious.equals(entry.getPreviousHash())) {
                    return broken(checked, unchained, entry,
                            "Lien rompu avec l'entrée précédente : une entrée a été supprimée ou insérée");
                }
                if (!AuditChain.hash(entry).equals(entry.getHash())) {
                    return broken(checked, unchained, entry, "Contenu modifié après son enregistrement");
                }
                expectedPrevious = entry.getHash();
                checked++;
            }
        } while (page.hasNext());

        return new AuditIntegrityReport(true, checked, unchained, null,
                "Journal intègre : " + checked + " entrée(s) vérifiée(s)"
                        + (unchained == 0 ? "" : ", " + unchained + " entrée(s) antérieure(s) à la chaîne"),
                LocalDateTime.now());
    }

    private static AuditIntegrityReport broken(long checked, long unchained, AuditLog entry, String reason) {
        return new AuditIntegrityReport(false, checked, unchained, entry.getId(),
                "Altération détectée à l'entrée #" + entry.getId() + " : " + reason,
                LocalDateTime.now());
    }
}
