package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.CreateDistributorRequest;
import com.hykon.iot_backend.dto.DistributorResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.exception.DuplicateResourceException;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributorServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DistributorRepository distributorRepository;

    @Mock
    private DistributorBranchRepository branchRepository;

    private DistributorService service;

    @BeforeEach
    void setUp() {
        service = new DistributorService(
                userRepository, distributorRepository, branchRepository
        );
    }

    private User account(Long id, RoleName role, String status) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(new Role(role));
        user.setStatus(status);
        return user;
    }

    private CreateDistributorRequest request() {
        CreateDistributorRequest request = new CreateDistributorRequest();
        request.setDistributorCode(" dist-a ");
        request.setName(" Distributor A ");
        request.setAdministratorUserId(20L);
        request.setBranchCode(" main ");
        request.setBranchName(" Main Branch ");
        request.setLocation(" Kochi ");
        return request;
    }

    private void allowCreator() {
        when(userRepository.findByEmailWithRole("creator@example.com"))
                .thenReturn(Optional.of(
                        account(1L, RoleName.HYKON_ADMIN, "ACTIVE")
                ));
    }

    private void allowAdministrator() {
        when(userRepository.findById(20L))
                .thenReturn(Optional.of(
                        account(20L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE")
                ));
    }

    @Test
    void createsDistributorAndBranchWithCorrectAccountRelationship() {
        allowCreator();
        allowAdministrator();

        when(distributorRepository.saveAndFlush(any(Distributor.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(branchRepository.saveAndFlush(any(DistributorBranch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DistributorResponse response =
                service.create(request(), "creator@example.com");

        ArgumentCaptor<Distributor> distributor =
                ArgumentCaptor.forClass(Distributor.class);
        ArgumentCaptor<DistributorBranch> branch =
                ArgumentCaptor.forClass(DistributorBranch.class);

        verify(distributorRepository).saveAndFlush(distributor.capture());
        verify(branchRepository).saveAndFlush(branch.capture());

        assertEquals("DIST-A", response.distributorCode());
        assertEquals("Distributor A", response.name());
        assertEquals(Long.valueOf(20), response.administratorUserId());
        assertEquals("MAIN", response.branchCode());
        assertEquals("Main Branch", response.branchName());
        assertEquals("Kochi", response.location());
        assertEquals("ACTIVE", response.status());
        assertEquals("ACTIVE", response.branchStatus());

        assertSame(distributor.getValue(), branch.getValue().getDistributor());
        assertEquals(
                RoleName.HYKON_ADMIN,
                distributor.getValue().getCreatedBy().getRole().getName()
        );
    }

    @Test
    void unauthorizedOrInactiveCreatorsCannotCreateDistributors() {
        for (User creator : new User[] {
                account(1L, RoleName.USER, "ACTIVE"),
                account(1L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE"),
                account(1L, RoleName.HYKON_ADMIN, "INACTIVE")
        }) {
            when(userRepository.findByEmailWithRole("creator@example.com"))
                    .thenReturn(Optional.of(creator));

            assertThrows(AccessDeniedException.class,
                    () -> service.create(request(), "creator@example.com"));
        }

        verifyNoInteractions(distributorRepository, branchRepository);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void missingCreatorIdentityIsDenied() {
        assertThrows(AccessDeniedException.class,
                () -> service.create(request(), null));

        verifyNoInteractions(
                userRepository, distributorRepository, branchRepository
        );
    }

    @Test
    void assignedAccountMustBeAnActiveDistributorAdmin() {
        allowCreator();

        for (User administrator : new User[] {
                account(20L, RoleName.USER, "ACTIVE"),
                account(20L, RoleName.HYKON_ADMIN, "ACTIVE"),
                account(20L, RoleName.DISTRIBUTOR_ADMIN, "INACTIVE")
        }) {
            when(userRepository.findById(20L))
                    .thenReturn(Optional.of(administrator));

            assertThrows(IllegalArgumentException.class,
                    () -> service.create(request(), "creator@example.com"));
        }

        verifyNoInteractions(distributorRepository, branchRepository);
    }

    @Test
    void duplicateDistributorCodeIsRejected() {
        allowCreator();
        allowAdministrator();

        when(distributorRepository.findByCode("DIST-A"))
                .thenReturn(Optional.of(new Distributor()));

        assertThrows(DuplicateResourceException.class,
                () -> service.create(request(), "creator@example.com"));

        verify(distributorRepository, never()).saveAndFlush(any());
        verifyNoInteractions(branchRepository);
    }

    @Test
    void administratorCannotBeAssignedToTwoDistributors() {
        allowCreator();
        allowAdministrator();

        when(distributorRepository.existsByAdministrator_Id(20L))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> service.create(request(), "creator@example.com"));

        verify(distributorRepository, never()).saveAndFlush(any());
        verifyNoInteractions(branchRepository);
    }

    @Test
    void databaseUniquenessConflictIsReportedAsConflict() {
        allowCreator();
        allowAdministrator();

        when(distributorRepository.saveAndFlush(any(Distributor.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(DuplicateResourceException.class,
                () -> service.create(request(), "creator@example.com"));

        verifyNoInteractions(branchRepository);
    }
}