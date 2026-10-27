package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DistributorAccessRequest;
import com.hykon.iot_backend.dto.DistributorAccessResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributorAccessServiceTest {

    private static final String EMAIL = "distributor@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DistributorRepository distributorRepository;

    @Mock
    private DistributorBranchRepository branchRepository;

    private DistributorAccessService service;

    @BeforeEach
    void setUp() {
        service = new DistributorAccessService(
                userRepository, distributorRepository, branchRepository
        );
    }

    private DistributorAccessRequest request() {
        DistributorAccessRequest request = new DistributorAccessRequest();
        request.setDistributorCode("DIST-A");
        request.setBranchCode("MAIN");
        request.setLocation("EKM");
        return request;
    }

    private User allowAccount() {
        User account = new User();
        ReflectionTestUtils.setField(account, "id", 22L);
        account.setRole(new Role(RoleName.DISTRIBUTOR_ADMIN));
        account.setStatus("ACTIVE");

        when(userRepository.findByEmailWithRole(EMAIL))
                .thenReturn(Optional.of(account));

        return account;
    }

    private Distributor allowDistributor() {
        User account = allowAccount();

        Distributor distributor = new Distributor();
        ReflectionTestUtils.setField(distributor, "id", 1L);
        distributor.setDistributorCode("DIST-A");
        distributor.setName("DEV");
        distributor.setAdministrator(account);
        distributor.setStatus("ACTIVE");

        when(distributorRepository.findByAdministrator_Id(22L))
                .thenReturn(Optional.of(distributor));

        return distributor;
    }

    private DistributorBranch allowBranch() {
        Distributor distributor = allowDistributor();

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 1L);
        branch.setDistributor(distributor);
        branch.setBranchCode("MAIN");
        branch.setName("ERNAKULAM");
        branch.setLocation("EKM");
        branch.setStatus("ACTIVE");

        when(branchRepository.findByDistributorAndCode(1L, "MAIN"))
                .thenReturn(Optional.of(branch));

        return branch;
    }

    private void assertVerificationFailed(
            DistributorAccessRequest request
    ) {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.verify(request, EMAIL)
        );

        assertEquals(
                "Distributor verification failed",
                error.getMessage()
        );
    }

    private void assertForbidden(Runnable action) {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                action::run
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void correctDetailsReturnStoredDistributorAndBranch() {
        allowBranch();

        DistributorAccessResponse response =
                service.verify(request(), EMAIL);

        assertEquals(Long.valueOf(1), response.distributorId());
        assertEquals("DIST-A", response.distributorCode());
        assertEquals("DEV", response.distributorName());
        assertEquals(Long.valueOf(1), response.branchId());
        assertEquals("MAIN", response.branchCode());
        assertEquals("ERNAKULAM", response.branchName());
        assertEquals("EKM", response.location());

        verify(distributorRepository).findByAdministrator_Id(22L);
        verify(branchRepository).findByDistributorAndCode(1L, "MAIN");
        verifyNoMoreInteractions(distributorRepository, branchRepository);
    }

    @Test
    void distributorCodeAndLocationIgnoreCaseAndOuterSpaces() {
        allowBranch();
        DistributorAccessRequest request = request();
        request.setDistributorCode(" dist-a ");
        request.setBranchCode(" MAIN ");
        request.setLocation(" ekm ");

        DistributorAccessResponse response = service.verify(request, EMAIL);

        assertEquals("DIST-A", response.distributorCode());
        assertEquals("EKM", response.location());
        verify(branchRepository).findByDistributorAndCode(1L, "MAIN");
    }

    @Test
    void anotherDistributorsCodeIsRejectedBeforeBranchLookup() {
        allowDistributor();
        DistributorAccessRequest request = request();
        request.setDistributorCode("DIST-B");

        assertVerificationFailed(request);

        verify(distributorRepository).findByAdministrator_Id(22L);
        verifyNoMoreInteractions(distributorRepository);
        verifyNoInteractions(branchRepository);
    }

    @Test
    void unknownBranchIsRejectedWithinAuthenticatedDistributorScope() {
        allowDistributor();
        DistributorAccessRequest request = request();
        request.setBranchCode("OTHER");

        assertVerificationFailed(request);

        verify(branchRepository).findByDistributorAndCode(1L, "OTHER");
        verifyNoMoreInteractions(branchRepository);
    }

    @Test
    void branchBelongingToAnotherDistributorIsRejected() {
        DistributorBranch branch = allowBranch();

        Distributor otherDistributor = new Distributor();
        ReflectionTestUtils.setField(otherDistributor, "id", 2L);
        branch.setDistributor(otherDistributor);

        assertVerificationFailed(request());
    }

    @Test
    void incorrectLocationIsRejected() {
        allowBranch();
        DistributorAccessRequest request = request();
        request.setLocation("OTHER LOCATION");

        assertVerificationFailed(request);
    }

    @Test
    void inactiveBranchIsRejected() {
        DistributorBranch branch = allowBranch();
        branch.setStatus("INACTIVE");

        assertVerificationFailed(request());
    }

    @Test
    void inactiveDistributorIsRejectedBeforeBranchLookup() {
        Distributor distributor = allowDistributor();
        distributor.setStatus("INACTIVE");

        assertVerificationFailed(request());

        verifyNoInteractions(branchRepository);
    }

    @Test
    void unlinkedDistributorAccountIsRejected() {
        allowAccount();

        assertVerificationFailed(request());

        verify(distributorRepository).findByAdministrator_Id(22L);
        verifyNoInteractions(branchRepository);
    }

    @Test
    void inactiveAccountIsForbiddenBeforeDistributorLookup() {
        User account = allowAccount();
        account.setStatus("INACTIVE");

        assertForbidden(() -> service.verify(request(), EMAIL));

        verifyNoInteractions(distributorRepository, branchRepository);
    }

    @Test
    void customerAndCompanyAdminCannotUseDistributorVerification() {
        User account = allowAccount();

        account.setRole(new Role(RoleName.USER));
        assertForbidden(() -> service.verify(request(), EMAIL));

        account.setRole(new Role(RoleName.HYKON_ADMIN));
        assertForbidden(() -> service.verify(request(), EMAIL));

        verifyNoInteractions(distributorRepository, branchRepository);
    }

    @Test
    void missingIdentityIsForbiddenWithoutRepositoryCalls() {
        assertForbidden(() -> service.verify(request(), null));
        assertForbidden(() -> service.verify(request(), " "));

        verifyNoInteractions(
                userRepository, distributorRepository, branchRepository
        );
    }

    @Test
    void unknownAccountIsForbiddenBeforeDistributorLookup() {
        assertForbidden(() -> service.verify(request(), EMAIL));

        verifyNoInteractions(distributorRepository, branchRepository);
    }

    @Test
    void missingVerificationFieldsAreRejectedBeforeDistributorLookup() {
        allowAccount();

        assertVerificationFailed(null);

        DistributorAccessRequest request = request();
        request.setDistributorCode(" ");
        assertVerificationFailed(request);

        request = request();
        request.setBranchCode(null);
        assertVerificationFailed(request);

        request = request();
        request.setLocation("");
        assertVerificationFailed(request);

        verifyNoInteractions(distributorRepository, branchRepository);
    }
}