package com.smartdeploy.portal;

import com.smartdeploy.incident.IncidentSimilarity;
import com.smartdeploy.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Base de connaissances : les problèmes déjà résolus, proposés avant qu'un développeur n'écrive à l'équipe.
 * La recherche réutilise le moteur TF-IDF de la mémoire des incidents.
 */
@Service
public class KnowledgeService {

    public record ArticleRequest(String title, String symptoms, String cause, String solution, String signature) {
    }

    public record ArticleResponse(Long id, String title, String symptoms, String cause, String solution, String signature,
                                  int helpfulCount, Long sourceClaimId, String authorEmail, LocalDateTime createdAt, Integer score) {
    }

    private final KnowledgeArticleRepository articles;
    private final ClaimRepository claims;

    public KnowledgeService(KnowledgeArticleRepository articles, ClaimRepository claims) {
        this.articles = articles;
        this.claims = claims;
    }

    @Transactional(readOnly = true)
    public List<ArticleResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return articles.findAllByOrderByCreatedAtDesc().stream().map(a -> toResponse(a, null)).toList();
        }
        return rank(query, 20, 0.05);
    }

    /** Les trois fiches les plus proches d'un texte (titre d'une réclamation, cause d'un échec). */
    @Transactional(readOnly = true)
    public List<ArticleResponse> suggest(String text) {
        return text == null || text.isBlank() ? List.of() : rank(text, 3, 0.12);
    }

    private List<ArticleResponse> rank(String text, int max, double minScore) {
        Map<Long, List<String>> corpus = new HashMap<>();
        Map<Long, KnowledgeArticle> byId = new HashMap<>();
        for (KnowledgeArticle a : articles.findAll()) {
            corpus.put(a.getId(), tokens(a));
            byId.put(a.getId(), a);
        }
        List<String> query = IncidentSimilarity.tokens(null, text, null, null, null);
        return IncidentSimilarity.rank(query, corpus, max, minScore).stream()
                .map(m -> toResponse(byId.get(m.id()), (int) Math.round(m.score() * 100)))
                .toList();
    }

    static List<String> tokens(KnowledgeArticle a) {
        return IncidentSimilarity.tokens(a.getSignature(), a.getTitle(), null,
                (a.getSymptoms() == null ? "" : a.getSymptoms()) + " " + (a.getCause() == null ? "" : a.getCause()), a.getSolution());
    }

    @Transactional
    public ArticleResponse create(ArticleRequest request, User author) {
        team(author);
        KnowledgeArticle a = new KnowledgeArticle();
        apply(a, request);
        a.setAuthorEmail(author.getEmail());
        a.setCreatedAt(LocalDateTime.now());
        return toResponse(articles.save(a), null);
    }

    @Transactional
    public ArticleResponse update(Long id, ArticleRequest request, User author) {
        team(author);
        KnowledgeArticle a = load(id);
        apply(a, request);
        a.setUpdatedAt(LocalDateTime.now());
        return toResponse(a, null);
    }

    @Transactional
    public void delete(Long id, User author) {
        team(author);
        articles.delete(load(id));
    }

    /** Publication d'une réclamation résolue : la fiche reprend son titre, sa description, sa cause et sa solution. */
    @Transactional
    public ArticleResponse fromClaim(Long claimId, User author) {
        team(author);
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NoSuchElementException("Réclamation introuvable : " + claimId));
        if (claim.getResolution() == null || claim.getResolution().isBlank()) {
            throw new IllegalStateException("Seule une réclamation résolue peut être publiée");
        }
        KnowledgeArticle a = new KnowledgeArticle();
        a.setTitle(claim.getTitle());
        a.setSymptoms(claim.getDescription());
        a.setCause(claim.getCause() == null ? null : ClaimService.label(claim.getCause()));
        a.setSolution(claim.getResolution());
        a.setSourceClaimId(claim.getId());
        a.setAuthorEmail(author.getEmail());
        a.setCreatedAt(LocalDateTime.now());
        return toResponse(articles.save(a), null);
    }

    @Transactional
    public ArticleResponse helpful(Long id) {
        KnowledgeArticle a = load(id);
        a.setHelpfulCount(a.getHelpfulCount() + 1);
        return toResponse(a, null);
    }

    private KnowledgeArticle load(Long id) {
        return articles.findById(id).orElseThrow(() -> new NoSuchElementException("Fiche introuvable : " + id));
    }

    private static void team(User user) {
        if (!ClaimService.isTeam(user)) {
            throw new AccessDeniedException("La base de connaissances est rédigée par l'équipe DevOps");
        }
    }

    private static void apply(KnowledgeArticle a, ArticleRequest r) {
        if (r == null || r.title() == null || r.title().isBlank() || r.solution() == null || r.solution().isBlank()) {
            throw new IllegalArgumentException("Le titre et la solution sont obligatoires");
        }
        a.setTitle(r.title().trim());
        a.setSymptoms(r.symptoms());
        a.setCause(r.cause());
        a.setSolution(r.solution().trim());
        a.setSignature(r.signature());
    }

    static ArticleResponse toResponse(KnowledgeArticle a, Integer score) {
        return new ArticleResponse(a.getId(), a.getTitle(), a.getSymptoms(), a.getCause(), a.getSolution(), a.getSignature(),
                a.getHelpfulCount(), a.getSourceClaimId(), a.getAuthorEmail(), a.getCreatedAt(), score);
    }
}
