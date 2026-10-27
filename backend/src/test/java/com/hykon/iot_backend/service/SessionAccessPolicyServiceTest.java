package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.InvalidTokenException;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionAccessPolicyServiceTest {

    private static final String EMAIL = "account@example.com";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginSessionService sessionService;

    @Mock
    private SessionAccessGrantRepository grantRepository;

    @InjectMocks
    private SessionAccessPolicyService service;

    private UsernamePasswordAuthenticationToken authentication() {
        var authentication = new UsernamePasswordAuthenticationToken(
                EMAIL,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        authentication.setDetails(SESSION_ID);
        return authentication;
    }

    private User user(Long id, RoleName role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(EMAIL);
        user.setRole(new Role(role));
        user.setStatus("ACTIVE");
        return user;
    }

    private User allowAccount(Long id, RoleName role) {
        User user = user(id, role);
        when(userRepository.findByEmailWithRole(EMAIL))
                .thenReturn(Optional.of(user));
        return user;
    }

    private DistributorBranch branch(User administrator) {
        Distributor distributor = new Distributor();
        ReflectionTestUtils.setField(distributor, "id", 1L);
        distributor.setAdministrator(administrator);
        distributor.setStatus("ACTIVE");

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 1L);
        branch.setDistributor(distributor);
        branch.setStatus("ACTIVE");
        return branch;
    }

    private SessionAccessGrant allowDeviceGrant() {
        User owner = allowAccount(16L, RoleName.USER);

        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 1L);
        device.setOwner(owner);
        device.setStatus("ACTIVE");
        device.setBranch(branch(user(22L, RoleName.DISTRIBUTOR_ADMIN)));

        SessionAccessGrant grant = new SessionAccessGrant();
        grant.setSessionId(SESSION_ID);
        grant.setDevice(device);
        grant.setDeviceVerifiedAt(LocalDateTime.now());

        when(grantRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(grant));

        return grant;
    }

    private SessionAccessGrant allowDistributorGrant() {
        User administrator = allowAccount(
                22L, RoleName.DISTRIBUTOR_ADMIN
        );

        SessionAccessGrant grant = new SessionAccessGrant();
        grant.setSessionId(SESSION_ID);
        grant.setBranch(branch(administrator));
        grant.setDistributorVerifiedAt(LocalDateTime.now());

        when(grantRepository.findById(SESSION_ID))
                .thenReturn(Optional.of(grant));

        return grant;
    }

    private void assertVerificationRequired() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requireVerified(authentication())
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("Access verification is required", error.getReason());
    }

    @Test
    void missingAuthenticationOrSessionIdentityReturnsUnauthorized() {
        ResponseStatusException missingAuthentication = assertThrows(
                ResponseStatusException.class,
                () -> service.requireVerified(null)
        );
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                missingAuthentication.getStatusCode()
        );

        var authentication = authentication();
        authentication.setDetails(null);

        ResponseStatusException missingSession = assertThrows(
                ResponseStatusException.class,
                () -> service.requireVerified(authentication)
        );
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                missingSession.getStatusCode()
        );

        verifyNoInteractions(
                userRepository, sessionService, grantRepository
        );
    }

    @Test
    void companyAdminNeedsActiveSessionButNoVerificationGrant() {
        allowAccount(5L, RoleName.HYKON_ADMIN);

        assertDoesNotThrow(
                () -> service.requireVerified(authentication())
        );

        verify(sessionService).requireActive(SESSION_ID, 5L);
        verifyNoInteractions(grantRepository);
    }

    @Test
    void unverifiedCustomerSessionIsForbidden() {
        allowAccount(16L, RoleName.USER);

        assertVerificationRequired();

        verify(sessionService).requireActive(SESSION_ID, 16L);
        verify(grantRepository).findById(SESSION_ID);
    }

    @Test
    void validCustomerDeviceGrantAllowsAccess() {
        allowDeviceGrant();

        assertDoesNotThrow(
                () -> service.requireVerified(authentication())
        );

        verify(sessionService).requireActive(SESSION_ID, 16L);
    }

    @Test
    void transferredDeviceInvalidatesPreviousVerification() {
        SessionAccessGrant grant = allowDeviceGrant();
        grant.getDevice().setOwner(user(19L, RoleName.USER));

        assertVerificationRequired();
    }

    @Test
    void inactiveDeviceInvalidatesVerification() {
        SessionAccessGrant grant = allowDeviceGrant();
        grant.getDevice().setStatus("INACTIVE");

        assertVerificationRequired();
    }

    @Test
    void inactiveBranchOrDistributorInvalidatesCustomerVerification() {
        SessionAccessGrant grant = allowDeviceGrant();
        DistributorBranch branch = grant.getDevice().getBranch();

        branch.setStatus("INACTIVE");
        assertVerificationRequired();

        branch.setStatus("ACTIVE");
        branch.getDistributor().setStatus("INACTIVE");
        assertVerificationRequired();
    }

    @Test
    void unverifiedDistributorSessionIsForbidden() {
        allowAccount(22L, RoleName.DISTRIBUTOR_ADMIN);

        assertVerificationRequired();

        verify(sessionService).requireActive(SESSION_ID, 22L);
    }

    @Test
    void validDistributorGrantAllowsAccess() {
        allowDistributorGrant();

        assertDoesNotThrow(
                () -> service.requireVerified(authentication())
        );

        verify(sessionService).requireActive(SESSION_ID, 22L);
    }

    @Test
    void changedDistributorAdministratorInvalidatesVerification() {
        SessionAccessGrant grant = allowDistributorGrant();
        grant.getBranch().getDistributor().setAdministrator(
                user(23L, RoleName.DISTRIBUTOR_ADMIN)
        );

        assertVerificationRequired();
    }

    @Test
    void inactiveBranchOrDistributorInvalidatesDistributorVerification() {
        SessionAccessGrant grant = allowDistributorGrant();
        DistributorBranch branch = grant.getBranch();

        branch.setStatus("INACTIVE");
        assertVerificationRequired();

        branch.setStatus("ACTIVE");
        branch.getDistributor().setStatus("INACTIVE");
        assertVerificationRequired();
    }

    @Test
    void missingVerificationTimestampDoesNotAllowAccess() {
        SessionAccessGrant grant = allowDeviceGrant();
        grant.setDeviceVerifiedAt(null);

        assertVerificationRequired();
    }

    @Test
    void verificationDoesNotTransferToAnotherLoginSession() {
        allowDeviceGrant();

        var authentication = authentication();
        service.requireVerified(authentication);

        UUID otherSession = UUID.randomUUID();
        authentication.setDetails(otherSession);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requireVerified(authentication)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(sessionService).requireActive(otherSession, 16L);
        verify(grantRepository).findById(otherSession);
    }

    @Test
    void invalidSessionIsRejectedBeforeGrantLookup() {
        allowAccount(16L, RoleName.USER);

        when(sessionService.requireActive(SESSION_ID, 16L))
                .thenThrow(new InvalidTokenException(
                        "Login session is invalid, expired, or revoked"
                ));

        assertThrows(InvalidTokenException.class,
                () -> service.requireVerified(authentication()));

        verifyNoInteractions(grantRepository);
    }

    @Test
    void inactiveAccountIsRejectedBeforeSessionAndGrantLookup() {
        User account = allowAccount(16L, RoleName.USER);
        account.setStatus("INACTIVE");

        assertVerificationRequired();

        verifyNoInteractions(sessionService, grantRepository);
    }
}