package com.smartdeploy.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /*
     * =========================================================
     * INSCRIPTION PUBLIQUE
     * =========================================================
     */

    public UserResponse register(RegisterRequest request) {

        String normalizedEmail = normalizeEmail(request.email());

        checkEmailAvailability(normalizedEmail);

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(Role.VIEWER)
                .enabled(true)
                .deleted(false)
                .tokenVersion(0)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        return toResponse(savedUser);
    }

    /*
     * =========================================================
     * CRÉATION PAR UN ADMINISTRATEUR
     * =========================================================
     */

    public UserResponse create(CreateUserRequest request) {

        String normalizedEmail = normalizeEmail(request.email());

        checkEmailAvailability(normalizedEmail);

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .enabled(true)
                .deleted(false)
                .tokenVersion(0)
                .build();

        User savedUser = userRepository.saveAndFlush(user);

        return toResponse(savedUser);
    }

    /*
     * =========================================================
     * CONSULTATION DES UTILISATEURS
     * =========================================================
     */

    @Transactional(readOnly = true)
    public List<UserResponse> findAll() {

        return userRepository.findAllByDeletedFalse()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {

        User user = findActiveUserById(id);

        return toResponse(user);
    }

    /*
     * =========================================================
     * MODIFICATION ADMINISTRATIVE
     * =========================================================
     */

    public UserResponse update(
            Long id,
            UpdateUserRequest request
    ) {
        User user = findActiveUserById(id);

        String normalizedEmail = normalizeEmail(request.email());

        boolean emailChanged =
                !user.getEmail().equalsIgnoreCase(normalizedEmail);

        if (emailChanged) {
            checkEmailAvailability(normalizedEmail);
        }

        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(normalizedEmail);

        if (emailChanged) {
            revokeExistingTokens(user);
        }

        User updatedUser = userRepository.saveAndFlush(user);

        return toResponse(updatedUser);
    }

    /*
     * =========================================================
     * MODIFICATION DU PROFIL CONNECTÉ
     * =========================================================
     */

    public UserResponse updateCurrentProfile(
            User currentUser,
            UpdateProfileRequest request
    ) {
        String normalizedEmail = normalizeEmail(request.email());

        boolean emailChanged =
                !currentUser.getEmail().equalsIgnoreCase(normalizedEmail);

        if (emailChanged) {
            checkEmailAvailability(normalizedEmail);
        }

        currentUser.setFirstName(request.firstName().trim());
        currentUser.setLastName(request.lastName().trim());
        currentUser.setEmail(normalizedEmail);

        /*
         * Le JWT contient l'adresse email comme subject.
         * Si l'email change, les anciens tokens doivent être révoqués.
         */
        if (emailChanged) {
            revokeExistingTokens(currentUser);
        }

        User updatedUser =
                userRepository.saveAndFlush(currentUser);

        return toResponse(updatedUser);
    }

    /*
     * =========================================================
     * MODIFICATION DU RÔLE
     * =========================================================
     */

    public UserResponse updateRole(
            Long id,
            UpdateRoleRequest request
    ) {
        User user = findActiveUserById(id);

        boolean removingAdminRole =
                user.getRole() == Role.ADMIN
                        && request.role() != Role.ADMIN;

        if (removingAdminRole) {
            ensureNotLastActiveAdmin(user);
        }

        /*
         * On ne fait rien de sensible si le rôle demandé
         * est déjà le rôle actuel.
         */
        if (user.getRole() != request.role()) {
            user.setRole(request.role());
            revokeExistingTokens(user);
        }

        User updatedUser = userRepository.saveAndFlush(user);

        return toResponse(updatedUser);
    }

    /*
     * =========================================================
     * ACTIVATION ET DÉSACTIVATION
     * =========================================================
     */

    public UserResponse updateStatus(
            Long id,
            UpdateStatusRequest request
    ) {
        User user = findActiveUserById(id);

        boolean disablingUser =
                user.isEnabled() && !request.enabled();

        if (disablingUser) {
            ensureNotLastActiveAdmin(user);
        }

        if (user.isEnabled() != request.enabled()) {
            user.setEnabled(request.enabled());
            revokeExistingTokens(user);
        }

        User updatedUser = userRepository.saveAndFlush(user);

        return toResponse(updatedUser);
    }

    /*
     * =========================================================
     * CHANGEMENT DE MOT DE PASSE
     * =========================================================
     */

    public void changePassword(
            User currentUser,
            ChangePasswordRequest request
    ) {
        boolean oldPasswordMatches =
                passwordEncoder.matches(
                        request.oldPassword(),
                        currentUser.getPassword()
                );

        if (!oldPasswordMatches) {
            throw new InvalidPasswordException(
                    "L'ancien mot de passe est incorrect"
            );
        }

        boolean samePassword =
                passwordEncoder.matches(
                        request.newPassword(),
                        currentUser.getPassword()
                );

        if (samePassword) {
            throw new InvalidPasswordException(
                    "Le nouveau mot de passe doit être différent de l'ancien"
            );
        }

        currentUser.setPassword(
                passwordEncoder.encode(request.newPassword())
        );

        revokeExistingTokens(currentUser);

        userRepository.saveAndFlush(currentUser);
    }

    /*
     * =========================================================
     * SUPPRESSION LOGIQUE
     * =========================================================
     */

    public void softDelete(
            Long id,
            User currentUser
    ) {
        User user = findActiveUserById(id);

        if (user.getId().equals(currentUser.getId())) {
            throw new IllegalStateException(
                    "Vous ne pouvez pas supprimer votre propre compte"
            );
        }

        ensureNotLastActiveAdmin(user);

        user.setDeleted(true);
        user.setEnabled(false);

        revokeExistingTokens(user);

        userRepository.saveAndFlush(user);
    }

    /*
     * =========================================================
     * RESTAURATION
     * =========================================================
     */

    public UserResponse restore(Long id) {

        /*
         * Ici, on utilise findById() et non
         * findByIdAndDeletedFalse(), car on cherche justement
         * un utilisateur supprimé.
         */
        User user = userRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Utilisateur introuvable avec l'identifiant : "
                                        + id
                        )
                );

        if (!user.isDeleted()) {
            throw new IllegalStateException(
                    "Cet utilisateur n'est pas supprimé"
            );
        }

        user.setDeleted(false);
        user.setEnabled(true);

        revokeExistingTokens(user);

        User restoredUser = userRepository.saveAndFlush(user);

        return toResponse(restoredUser);
    }

    /*
     * =========================================================
     * MÉTHODES PRIVÉES
     * =========================================================
     */

    private User findActiveUserById(Long id) {

        return userRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Utilisateur introuvable avec l'identifiant : "
                                        + id
                        )
                );
    }

    private String normalizeEmail(String email) {

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private void checkEmailAvailability(String email) {

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException(email);
        }
    }

    /*
     * Invalide tous les JWT précédemment générés pour l'utilisateur.
     */
    private void revokeExistingTokens(User user) {

        user.setTokenVersion(
                user.getTokenVersion() + 1
        );
    }

    /*
     * Empêche la suppression, la désactivation ou le changement
     * de rôle du dernier administrateur actif.
     */
    private void ensureNotLastActiveAdmin(User user) {

        boolean activeAdmin =
                user.getRole() == Role.ADMIN
                        && user.isEnabled()
                        && !user.isDeleted();

        if (!activeAdmin) {
            return;
        }

        long activeAdminCount =
                userRepository
                        .countByRoleAndEnabledTrueAndDeletedFalse(
                                Role.ADMIN
                        );

        if (activeAdminCount <= 1) {
            throw new LastAdminProtectionException(
                    "Cette action est impossible, car cet utilisateur "
                            + "est le dernier administrateur actif"
            );
        }
    }

    /*
     * Transforme l'entité User en DTO de réponse.
     * Le mot de passe et tokenVersion ne sont jamais exposés.
     */
    private UserResponse toResponse(User user) {

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

    @Transactional(readOnly = true)
    public UserPageResponse searchUsers(
            int page,
            int size,
            String search,
            Role role,
            Boolean enabled
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(
                        Sort.Order.asc("lastName"),
                        Sort.Order.asc("firstName")
                )
        );

        Specification<User> specification =
                UserSpecifications.notDeleted()
                        .and(UserSpecifications.containsSearch(search))
                        .and(UserSpecifications.hasRole(role))
                        .and(UserSpecifications.hasEnabled(enabled));

        Page<User> userPage =
                userRepository.findAll(specification, pageable);

        return new UserPageResponse(
                userPage.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                userPage.getNumber(),
                userPage.getSize(),
                userPage.getTotalElements(),
                userPage.getTotalPages(),
                userPage.isFirst(),
                userPage.isLast()
        );
    }
}