package com.smartdeploy.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void register_shouldCreateViewerWithEncodedPassword() {

        RegisterRequest request = new RegisterRequest(
                " Ruqaia ",
                " Sabta ",
                " RUQAIA@SMARTDEPLOY.COM ",
                "Password123"
        );

        when(userRepository.existsByEmailIgnoreCase(
                "ruqaia@smartdeploy.com"
        )).thenReturn(false);

        when(passwordEncoder.encode("Password123"))
                .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);

                    user.setId(1L);
                    user.setCreatedAt(LocalDateTime.now());
                    user.setUpdatedAt(LocalDateTime.now());

                    return user;
                });

        UserResponse response = userService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Ruqaia", response.firstName());
        assertEquals("Sabta", response.lastName());
        assertEquals(
                "ruqaia@smartdeploy.com",
                response.email()
        );
        assertEquals(Role.VIEWER, response.role());
        assertTrue(response.enabled());
        assertFalse(response.deleted());

        verify(passwordEncoder)
                .encode("Password123");

        verify(userRepository)
                .saveAndFlush(any(User.class));
    }
    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest(
                "Ruqaia",
                "Sabta",
                "ruqaia@smartdeploy.com",
                "Password123"
        );

        when(userRepository.existsByEmailIgnoreCase(
                "ruqaia@smartdeploy.com"
        )).thenReturn(true);

        EmailAlreadyExistsException exception =
                assertThrows(
                        EmailAlreadyExistsException.class,
                        () -> userService.register(request)
                );

        assertTrue(
                exception.getMessage()
                        .contains("ruqaia@smartdeploy.com")
        );

        verify(userRepository, never())
                .saveAndFlush(any(User.class));

        verify(passwordEncoder, never())
                .encode(anyString());
    }
    @Test
    void findById_shouldThrowWhenUserDoesNotExist() {

        when(userRepository.findByIdAndDeletedFalse(999L))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> userService.findById(999L)
                );

        assertEquals(
                "Utilisateur introuvable avec l'identifiant : 999",
                exception.getMessage()
        );
    }
    @Test
    void updateRole_shouldProtectLastActiveAdmin() {

        User admin = createUser(
                1L,
                "admin@smartdeploy.com",
                Role.ADMIN,
                true,
                false
        );

        UpdateRoleRequest request =
                new UpdateRoleRequest(Role.DEVOPS);

        when(userRepository.findByIdAndDeletedFalse(1L))
                .thenReturn(Optional.of(admin));

        when(userRepository.countByRoleAndEnabledTrueAndDeletedFalse(
                Role.ADMIN))
                .thenReturn(1L);

        LastAdminProtectionException exception =
                assertThrows(
                        LastAdminProtectionException.class,
                        () -> userService.updateRole(1L, request)
                );

        assertTrue(exception.getMessage()
                .contains("dernier administrateur actif"));

        assertEquals(Role.ADMIN, admin.getRole());

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }
    private User createUser(
            Long id,
            String email,
            Role role,
            boolean enabled,
            boolean deleted
    ) {

        LocalDateTime now = LocalDateTime.now();

        return User.builder()
                .id(id)
                .firstName("Test")
                .lastName("User")
                .email(email)
                .password("encoded-password")
                .role(role)
                .enabled(enabled)
                .deleted(deleted)
                .tokenVersion(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
    @Test
    void changePassword_shouldThrowWhenOldPasswordIsIncorrect() {

        User user = createUser(
                1L,
                "ruqaia@smartdeploy.com",
                Role.ADMIN,
                true,
                false
        );

        user.setPassword("encoded-old-password");

        ChangePasswordRequest request =
                new ChangePasswordRequest(
                        "WrongPassword",
                        "NewPassword123"
                );

        when(passwordEncoder.matches(
                "WrongPassword",
                "encoded-old-password"
        )).thenReturn(false);

        InvalidPasswordException exception =
                assertThrows(
                        InvalidPasswordException.class,
                        () -> userService.changePassword(user, request)
                );

        assertTrue(
                exception.getMessage()
                        .contains("ancien mot de passe")
        );

        verify(passwordEncoder, never())
                .encode(anyString());

        verify(userRepository, never())
                .saveAndFlush(any(User.class));
    }
    @Test
    void changePassword_shouldUpdatePasswordAndIncrementTokenVersion() {

        User user = createUser(
                1L,
                "ruqaia@smartdeploy.com",
                Role.ADMIN,
                true,
                false
        );

        user.setPassword("encoded-old-password");
        user.setTokenVersion(5);

        ChangePasswordRequest request =
                new ChangePasswordRequest(
                        "OldPassword123",
                        "NewPassword456"
                );

        when(passwordEncoder.matches(
                "OldPassword123",
                "encoded-old-password"
        )).thenReturn(true);

        when(passwordEncoder.matches(
                "NewPassword456",
                "encoded-old-password"
        )).thenReturn(false);

        when(passwordEncoder.encode("NewPassword456"))
                .thenReturn("encoded-new-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.changePassword(user, request);

        assertEquals(
                "encoded-new-password",
                user.getPassword()
        );

        assertEquals(
                6,
                user.getTokenVersion()
        );

        verify(passwordEncoder)
                .encode("NewPassword456");

        verify(userRepository)
                .saveAndFlush(user);
    }
    @Test
    void softDelete_shouldMarkUserDeletedAndDisabled() {

        User currentAdmin = createUser(
                1L,
                "admin@smartdeploy.com",
                Role.ADMIN,
                true,
                false
        );

        User targetUser = createUser(
                2L,
                "user@smartdeploy.com",
                Role.DEVELOPER,
                true,
                false
        );

        targetUser.setTokenVersion(3);

        when(userRepository.findByIdAndDeletedFalse(2L))
                .thenReturn(Optional.of(targetUser));

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.softDelete(2L, currentAdmin);

        assertTrue(targetUser.isDeleted());
        assertFalse(targetUser.isEnabled());
        assertEquals(4, targetUser.getTokenVersion());

        verify(userRepository)
                .saveAndFlush(targetUser);

    }

    @Test
    void restore_shouldEnableAndRestoreUser() {

        User targetUser = createUser(
                2L,
                "user@smartdeploy.com",
                Role.DEVELOPER,
                false,
                true
        );

        targetUser.setTokenVersion(7);

        when(userRepository.findById(2L))
                .thenReturn(Optional.of(targetUser));

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.restore(2L);

        assertFalse(targetUser.isDeleted());
        assertTrue(targetUser.isEnabled());
        assertEquals(8, targetUser.getTokenVersion());

        verify(userRepository)
                .saveAndFlush(targetUser);
    }
}