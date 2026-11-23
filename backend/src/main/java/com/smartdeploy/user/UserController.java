package com.smartdeploy.user;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return userService.register(request);
    }



    public record ApprovalRequest(Role role) {
    }

    public record TokenRequest(String token) {
    }

    public record EmailRequest(String email) {
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private EmailVerificationService emailVerification;

    /** RG-U3 : confirmation de l'adresse par le lien reçu (accessible sans connexion). */
    @PostMapping("/verify-email")
    public java.util.Map<String, String> verifyEmail(@RequestBody TokenRequest body) {
        if (emailVerification == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Vérification indisponible");
        }
        try {
            User user = emailVerification.verify(body == null ? null : body.token());
            return java.util.Map.of("email", user.getEmail());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Renvoie un lien de confirmation ; la réponse ne révèle jamais si l'adresse existe. */
    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@RequestBody EmailRequest body) {
        if (emailVerification != null) {
            emailVerification.resend(body == null ? null : body.email());
        }
    }

    /** Comptes en attente de validation : la notification en temps réel de l'administrateur. */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public java.util.List<UserResponse> pending() {
        return userService.pendingAccounts();
    }

    /** RG-U1 : l'administrateur valide une inscription en choisissant le rôle. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse approve(@PathVariable Long id, @RequestBody(required = false) ApprovalRequest body) {
        try {
            return userService.approve(id, body == null ? null : body.role());
        } catch (IllegalStateException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void reject(@PathVariable Long id) {
        userService.reject(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse findById(@PathVariable Long id) {
        return userService.findById(id);
    }
    @GetMapping("/me")
    public UserResponse getCurrentUser(
            @AuthenticationPrincipal User user
    ) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole(),
                user.isEnabled(),
                user.isDeleted(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }


    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public UserPageResponse searchUsers(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            Role role,

            @RequestParam(required = false)
            Boolean enabled
    ) {
        return userService.searchUsers(
                page,
                size,
                search,
                role,
                enabled
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return userService.update(id, request);
    }
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return userService.updateRole(id, request);
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request
    ) {
        return userService.updateStatus(id, request);
    }
    @PutMapping("/change-password")
    public void changePassword(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(currentUser, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser
    ) {
        userService.softDelete(id, currentUser);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse create(
            @Valid @RequestBody CreateUserRequest request
    ) {
        return userService.create(request);
    }
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse restore(
            @PathVariable Long id
    ) {
        return userService.restore(id);
    }

    @PutMapping("/me")
    public UserResponse updateCurrentProfile(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateCurrentProfile(currentUser, request);
    }
}