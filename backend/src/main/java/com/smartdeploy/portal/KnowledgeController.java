package com.smartdeploy.portal;

import com.smartdeploy.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("isAuthenticated()")
public class KnowledgeController {

    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping
    public List<KnowledgeService.ArticleResponse> search(@RequestParam(required = false) String q) {
        return service.search(q);
    }

    @GetMapping("/suggest")
    public List<KnowledgeService.ArticleResponse> suggest(@RequestParam(required = false) String text) {
        return service.suggest(text);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeService.ArticleResponse create(@RequestBody KnowledgeService.ArticleRequest request, @AuthenticationPrincipal User user) {
        return handle(() -> service.create(request, user));
    }

    @PutMapping("/{id}")
    public KnowledgeService.ArticleResponse update(@PathVariable Long id, @RequestBody KnowledgeService.ArticleRequest request,
                                                   @AuthenticationPrincipal User user) {
        return handle(() -> service.update(id, request, user));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        handle(() -> {
            service.delete(id, user);
            return null;
        });
    }

    @PostMapping("/from-claim/{claimId}")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeService.ArticleResponse fromClaim(@PathVariable Long claimId, @AuthenticationPrincipal User user) {
        return handle(() -> service.fromClaim(claimId, user));
    }

    @PostMapping("/{id}/helpful")
    public KnowledgeService.ArticleResponse helpful(@PathVariable Long id) {
        return handle(() -> service.helpful(id));
    }

    private static <T> T handle(Supplier<T> action) {
        try {
            return action.get();
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
