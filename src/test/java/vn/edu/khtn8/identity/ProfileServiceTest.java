package vn.edu.khtn8.identity;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.khtn8.identity.dto.ChangePasswordRequest;
import vn.edu.khtn8.identity.dto.UpdateProfileRequest;
import vn.edu.khtn8.identity.entity.Role;
import vn.edu.khtn8.identity.entity.User;
import vn.edu.khtn8.identity.repository.UserRepository;
import vn.edu.khtn8.identity.security.AuthenticatedUser;
import vn.edu.khtn8.identity.service.AuthenticationService;
import vn.edu.khtn8.identity.service.ProfileService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final AuthenticationService authentication = mock(AuthenticationService.class);
    private final ProfileService service = new ProfileService(users, encoder, authentication);
    private final User user = new User();
    private AuthenticatedUser principal;

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setUsername("student"); user.setFullName("Học sinh"); user.setEmail("student@example.test");
        user.setRole(Role.STUDENT); user.setPasswordHash("old-hash");
        principal = AuthenticatedUser.from(user);
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(users.findLockedById(7L)).thenReturn(Optional.of(user));
        when(encoder.matches("OldPassword123!", "old-hash")).thenReturn(true);
    }

    @Test void profileContainsOnlyPublicAccountFields() {
        var response = service.profile(principal);
        assertEquals(7L, response.id()); assertEquals("student@example.test", response.email());
        assertEquals("STUDENT", response.role()); assertEquals("student", response.username());
        assertFalse(response.toString().contains("old-hash"));
    }

    @Test void updateNormalizesNameAndEmailWithoutChangingIdentityOrPassword() {
        var response = service.update(principal, new UpdateProfileRequest("  Tên mới  ", " NEW@Example.Test "));
        assertEquals("Tên mới", response.fullName()); assertEquals("new@example.test", response.email());
        assertEquals("student", user.getUsername()); assertEquals(Role.STUDENT, user.getRole());
        assertEquals("old-hash", user.getPasswordHash()); assertEquals(0, user.getAuthVersion());
        verify(users).existsByEmailIgnoreCaseAndIdNot("new@example.test", 7L);
        verify(users).saveAndFlush(user); verifyNoInteractions(authentication);
    }

    @Test void unchangedEmailIsAllowed() {
        assertEquals("student@example.test", service.update(principal,
                new UpdateProfileRequest("Học sinh", "student@example.test")).email());
        verify(users).existsByEmailIgnoreCaseAndIdNot("student@example.test", 7L);
    }

    @Test void duplicateEmailAndBlankNameNeverPersistChanges() {
        when(users.existsByEmailIgnoreCaseAndIdNot("other@example.test", 7L)).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.update(principal,
                new UpdateProfileRequest("Tên mới", "other@example.test"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.update(principal,
                new UpdateProfileRequest("   ", "student@example.test"))).getStatusCode());
        verify(users, never()).saveAndFlush(any());
        assertEquals("Học sinh", user.getFullName());
    }

    @Test void passwordChangeFlushesNewHashBeforeRevokingSessions() {
        when(encoder.encode("NewPassword123!")).thenReturn("new-hash");
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        service.changePassword(principal, new ChangePasswordRequest("OldPassword123!", "NewPassword123!", "NewPassword123!"), request, response);
        assertEquals("new-hash", user.getPasswordHash());
        var order = inOrder(users, authentication);
        order.verify(users).findLockedById(7L);
        order.verify(users).saveAndFlush(user);
        order.verify(authentication).logout(AuthenticatedUser.from(user), request, response);
    }

    @Test void incorrectCurrentPasswordIsValidationErrorNotAuthenticationError() {
        var failure = changeFailure("WrongPassword123!", "NewPassword123!", "NewPassword123!");
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertEquals("Mật khẩu hiện tại không đúng.", failure.getReason());
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(authentication);
    }

    @Test void confirmationAndReusedPasswordAreRejected() {
        assertEquals("Xác nhận mật khẩu mới không khớp.", changeFailure("OldPassword123!", "NewPassword123!", "Mismatch123!").getReason());
        assertEquals("Mật khẩu mới phải khác mật khẩu hiện tại.", changeFailure("OldPassword123!", "OldPassword123!", "OldPassword123!").getReason());
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(authentication);
    }

    @Test void utf8ByteLimitIsEnforcedBeforeHashingForEveryPasswordField() {
        String tooLong = "ế".repeat(25);
        for (var input : new ChangePasswordRequest[]{
                new ChangePasswordRequest(tooLong, "NewPassword123!", "NewPassword123!"),
                new ChangePasswordRequest("OldPassword123!", tooLong, tooLong),
                new ChangePasswordRequest("OldPassword123!", "NewPassword123!", tooLong)}) {
            assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, () -> service.changePassword(
                    principal, input, new MockHttpServletRequest(), new MockHttpServletResponse())).getStatusCode());
        }
        verifyNoInteractions(encoder, authentication);
    }

    @Test void disabledRevokedAndMissingAccountsCannotBeReadOrChanged() {
        user.setEnabled(false);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> service.profile(principal)).getStatusCode());
        user.setEnabled(true); user.setAuthVersion(1);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> service.update(principal,
                new UpdateProfileRequest("Tên mới", "new@example.test"))).getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, changeFailure("OldPassword123!", "NewPassword123!", "NewPassword123!").getStatusCode());
        when(users.findById(7L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class, () -> service.profile(principal)).getStatusCode());
        verify(users, never()).saveAndFlush(any()); verifyNoInteractions(authentication);
    }

    private ResponseStatusException changeFailure(String current, String next, String confirm) {
        return assertThrows(ResponseStatusException.class, () -> service.changePassword(principal,
                new ChangePasswordRequest(current, next, confirm), new MockHttpServletRequest(), new MockHttpServletResponse()));
    }
}