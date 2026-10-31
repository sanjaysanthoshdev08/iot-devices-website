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

import java.util.List;
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

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DeviceCustomerAccessRepository customerAccessRepository;

    private DistributorService service;

    @BeforeEach
    void setUp() {
        service = new DistributorService(
                userRepository, distributorRepository, branchRepository, deviceRepository, customerAccessRepository
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

    @Test
    void getDirectoryRequiresHykonAdminRole() {
        User user = account(10L, RoleName.USER, "ACTIVE");
        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));

        assertThrows(AccessDeniedException.class,
                () -> service.getDirectory("user@test.com"));

        User distAdmin = account(20L, RoleName.DISTRIBUTOR_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("dist@test.com")).thenReturn(Optional.of(distAdmin));

        assertThrows(AccessDeniedException.class,
                () -> service.getDirectory("dist@test.com"));
    }

    @Test
    void getDirectoryAppliesBoundedPagination() {
        User admin = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        Distributor dist = new Distributor();
        ReflectionTestUtils.setField(dist, "id", 100L);
        dist.setDistributorCode("DIST-100");
        dist.setName("Hykon North");
        dist.setStatus("ACTIVE");

        org.springframework.data.domain.Page<Distributor> distPage = new org.springframework.data.domain.PageImpl<>(
                List.of(dist), org.springframework.data.domain.PageRequest.of(0, 10), 1
        );
        when(distributorRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(distPage);

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 200L);
        branch.setDistributor(dist);
        branch.setBranchCode("BR-N");
        branch.setName("North Branch");
        branch.setLocation("Thrissur");
        branch.setStatus("ACTIVE");
        when(branchRepository.findByDistributor_IdIn(List.of(100L))).thenReturn(List.of(branch));

        Device dev = new Device();
        ReflectionTestUtils.setField(dev, "id", 300L);
        dev.setBranch(branch);
        dev.setDeviceCode("halo1200-000300");
        dev.setSerialNumber("2610000300");
        dev.setStatus("ACTIVE");
        when(deviceRepository.findByBranch_IdIn(List.of(200L))).thenReturn(List.of(dev));

        var directory = service.getDirectory("admin@hykon.com", 0, 10, 5, 5);

        assertNotNull(directory);
        assertEquals(1, directory.size());
        assertEquals("Hykon North", directory.get(0).name());
        assertEquals(1, directory.get(0).branches().size());
        assertEquals(1, directory.get(0).branches().get(0).devices().size());
    }

    @Test
    void getDistributorBranchesProvidesLazyExpansion() {
        User admin = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        DistributorBranch branch = new DistributorBranch();
        ReflectionTestUtils.setField(branch, "id", 200L);
        branch.setBranchCode("BR-1");
        branch.setName("Branch 1");
        branch.setLocation("Kochi");
        branch.setStatus("ACTIVE");

        org.springframework.data.domain.Page<DistributorBranch> page = new org.springframework.data.domain.PageImpl<>(
                List.of(branch), org.springframework.data.domain.PageRequest.of(0, 10), 1
        );
        when(branchRepository.findByDistributor_Id(eq(100L), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(deviceRepository.findByBranch_IdIn(List.of(200L))).thenReturn(List.of());

        var result = service.getDistributorBranches(100L, "admin@hykon.com", 0, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Branch 1", result.getContent().get(0).name());
    }

    @Test
    void getBranchDevicesProvidesLazyExpansion() {
        User admin = account(1L, RoleName.HYKON_ADMIN, "ACTIVE");
        when(userRepository.findByEmailWithRole("admin@hykon.com")).thenReturn(Optional.of(admin));

        Device dev = new Device();
        ReflectionTestUtils.setField(dev, "id", 500L);
        dev.setDeviceCode("halo1200-000500");
        dev.setSerialNumber("2610000500");
        dev.setStatus("ACTIVE");

        org.springframework.data.domain.Page<Device> page = new org.springframework.data.domain.PageImpl<>(
                List.of(dev), org.springframework.data.domain.PageRequest.of(0, 10), 1
        );
        when(deviceRepository.findByBranch_Id(eq(200L), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        var result = service.getBranchDevices(200L, "admin@hykon.com", 0, 10);
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("halo1200-000500", result.getContent().get(0).deviceCode());
    }
}