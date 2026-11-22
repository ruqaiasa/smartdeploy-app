package com.smartdeploy.portal;

import com.smartdeploy.application.Application;
import com.smartdeploy.user.Role;
import com.smartdeploy.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Portail 2.0 : visibilité des réclamations, qui doit agir, corrélation avec le code, précision de l'analyste. */
class PortalV2RulesTest {

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail("u" + id + "@smartdeploy.local");
        u.setRole(role);
        return u;
    }

    private static Claim claim(User requester, User owner) {
        Application app = new Application();
        app.setId(7L);
        app.setName("demo-finale");
        app.setOwner(owner);
        Claim c = new Claim();
        c.setRequester(requester);
        c.setApplication(app);
        c.setStatus(Claim.Status.OPEN);
        return c;
    }

    @Test
    void aClaimIsSeenByItsRequesterTheOwnerAndTheTeamOnly() {
        User karim = user(1, Role.DEVELOPER), sara = user(2, Role.DEVELOPER), owner = user(3, Role.DEVELOPER);
        Claim c = claim(karim, owner);
        assertTrue(ClaimService.canSee(c, karim));
        assertTrue(ClaimService.canSee(c, owner), "le propriétaire de l'application est concerné");
        assertTrue(ClaimService.canSee(c, user(4, Role.DEVOPS)));
        assertTrue(ClaimService.canSee(c, user(5, Role.ADMIN)));
        assertFalse(ClaimService.canSee(c, sara));
        assertFalse(ClaimService.canSee(c, user(6, Role.VIEWER)));
    }

    @Test
    void eachStatusSaysWhoMustAct() {
        assertEquals("TEAM", ClaimService.waitingFor(Claim.Status.OPEN));
        assertEquals("TEAM", ClaimService.waitingFor(Claim.Status.IN_PROGRESS));
        assertEquals("REQUESTER", ClaimService.waitingFor(Claim.Status.WAITING_REQUESTER));
        assertEquals("REQUESTER", ClaimService.waitingFor(Claim.Status.RESOLVED), "il doit confirmer ou rouvrir");
        assertNull(ClaimService.waitingFor(Claim.Status.CLOSED));
    }

    @Test
    void aFileChangedByTheVersionAndCitedInTheErrorIsTheSuspect() {
        String analysis = "erreur de compilation [error] /app/src/main/java/com/demo/messageservice.java:[38,9] ';' expected";
        List<String> changed = List.of("backend/src/main/java/com/demo/MessageService.java", "README.md", "backend/pom.xml");
        assertEquals(List.of("backend/src/main/java/com/demo/MessageService.java"), AnalysisInsightController.suspects(changed, analysis));
        assertTrue(AnalysisInsightController.suspects(List.of("frontend/src/App.vue"), analysis).isEmpty());
    }

    @Test
    void precisionCountsAPartialAnalysisAsHalfRight() {
        AnalysisFeedback ok = feedback(AnalysisFeedback.Verdict.CORRECT, "COMPILATION");
        AnalysisFeedback half = feedback(AnalysisFeedback.Verdict.PARTIAL, "CRASH_LOOP");
        AnalysisFeedback wrong = feedback(AnalysisFeedback.Verdict.WRONG, "CRASH_LOOP");
        AnalysisFeedback ok2 = feedback(AnalysisFeedback.Verdict.CORRECT, "COMPILATION");
        AnalysisInsightController.AnalystStats stats = AnalysisInsightController.statsOf(List.of(ok, half, wrong, ok2));
        assertEquals(4, stats.total());
        assertEquals(63, stats.precision(), "(2 + 0,5) / 4 = 62,5 %");
        assertEquals(2, stats.byCode().size());
        assertNull(AnalysisInsightController.statsOf(List.of()).precision());
    }

    private static AnalysisFeedback feedback(AnalysisFeedback.Verdict verdict, String code) {
        AnalysisFeedback f = new AnalysisFeedback();
        f.setVerdict(verdict);
        f.setCode(code);
        return f;
    }
}
