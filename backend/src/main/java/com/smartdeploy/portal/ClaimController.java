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

/** Réclamations : ouverture, fil, prise en charge, résolution, confirmation, réouverture. */
@RestController
@RequestMapping("/api/claims")
@PreAuthorize("hasAnyRole('DEVELOPER', 'DEVOPS', 'ADMIN')")
public class ClaimController {

    public record NewMessage(String message, boolean askRequester) {
    }

    public record Resolution(String resolution, com.smartdeploy.portal.Claim.Cause cause) {
    }

    public record Reason(String message) {
    }

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    @GetMapping
    public List<ClaimService.ClaimResponse> list(@AuthenticationPrincipal User user) {
        return service.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimService.ClaimResponse create(@RequestBody ClaimService.CreateClaim request, @AuthenticationPrincipal User user) {
        return handle(() -> service.create(request, user));
    }

    @GetMapping("/{id}")
    public ClaimService.ClaimResponse get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return handle(() -> service.get(id, user));
    }

    @GetMapping("/{id}/messages")
    public List<ClaimService.ClaimMessageResponse> thread(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return handle(() -> service.thread(id, user));
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimService.ClaimMessageResponse addMessage(@PathVariable Long id, @RequestBody NewMessage body,
                                                         @AuthenticationPrincipal User user) {
        return handle(() -> service.addMessage(id, user, body == null ? null : body.message(), body != null && body.askRequester()));
    }

    @PostMapping("/{id}/assign")
    public ClaimService.ClaimResponse assign(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return handle(() -> service.assign(id, user));
    }

    @PostMapping("/{id}/resolve")
    public ClaimService.ClaimResponse resolve(@PathVariable Long id, @RequestBody Resolution body, @AuthenticationPrincipal User user) {
        return handle(() -> service.resolve(id, user, body == null ? null : body.resolution(), body == null ? null : body.cause()));
    }

    @PostMapping("/{id}/confirm")
    public ClaimService.ClaimResponse confirm(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return handle(() -> service.confirm(id, user));
    }

    @PostMapping("/{id}/reopen")
    public ClaimService.ClaimResponse reopen(@PathVariable Long id, @RequestBody(required = false) Reason body,
                                             @AuthenticationPrincipal User user) {
        return handle(() -> service.reopen(id, user, body == null ? null : body.message()));
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
