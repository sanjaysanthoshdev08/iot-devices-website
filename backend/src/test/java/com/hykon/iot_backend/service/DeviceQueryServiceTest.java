package com.hykon.iot_backend.service;

import com.hykon.iot_backend.dto.DeviceResponse;
import com.hykon.iot_backend.entity.*;
import com.hykon.iot_backend.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceQueryServiceTest {

    private static final String EMAIL = "viewer@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeviceRepository deviceRepository;

    @Mock
    private DistributorRepository distributorRepository;

    private DeviceQueryService service;

    @BeforeEach
    void setUp() {
        service = new DeviceQueryService(
                userRepository, deviceRepository, distributorRepository
        );
    }

    private User allowAccount(RoleName role) {
        User account = new User();
        ReflectionTestUtils.setField(account, "id", 16L);
        account.setRole(new Role(role));
        account.setStatus("ACTIVE");

        when(userRepository.findByEmailWithRole(EMAIL))
                .thenReturn(Optional.of(account));

        return account;
    }

    private void allowDistributor() {
        Distributor distributor = new Distributor();
        distributor.setStatus("ACTIVE");

        when(distributorRepository.findByAdministrator_Id(16L))
                .thenReturn(Optional.of(distributor));
    }

    private Device device() {
        Device device = new Device();
        ReflectionTestUtils.setField(device, "id", 1L);
        device.setDeviceCode("HALO1200-1");
        device.setSerialNumber("SN-HYK-1200-01");
        device.setSystemName("Test HALO 1200");
        device.setStatus("ACTIVE");
        return device;
    }

    private Pageable pageable() {
        return PageRequest.of(
                0, 20, Sort.by(Sort.Direction.DESC, "id")
        );
    }

    private void assertStatus(
            HttpStatus status,
            Runnable action
    ) {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                action::run
        );
        assertEquals(status, error.getStatusCode());
    }

    @Test
    void customerListUsesAuthenticatedOwnerId() {
        allowAccount(RoleName.USER);
        when(deviceRepository.findByOwner_Id(16L, pageable()))
                .thenReturn(new PageImpl<>(
                        List.of(device()), pageable(), 1
                ));

        Page<DeviceResponse> result = service.list(EMAIL, 0, 20);

        assertEquals(1L, result.getTotalElements());
        assertEquals("HALO1200-1",
                result.getContent().get(0).deviceCode());
        verify(deviceRepository).findByOwner_Id(16L, pageable());
        verifyNoMoreInteractions(deviceRepository);
        verifyNoInteractions(distributorRepository);
    }

    @Test
    void customerCanReadOwnDevice() {
        allowAccount(RoleName.USER);
        when(deviceRepository.findByIdAndOwner_Id(1L, 16L))
                .thenReturn(Optional.of(device()));

        assertEquals(1L, service.get(1L, EMAIL).id());

        verify(deviceRepository).findByIdAndOwner_Id(1L, 16L);
        verifyNoMoreInteractions(deviceRepository);
    }

    @Test
    void otherCustomersDeviceAndMissingDeviceReturnSameNotFound() {
        allowAccount(RoleName.USER);

        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.get(1L, EMAIL));
        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.get(999L, EMAIL));

        verify(deviceRepository).findByIdAndOwner_Id(1L, 16L);
        verify(deviceRepository).findByIdAndOwner_Id(999L, 16L);
        verifyNoMoreInteractions(deviceRepository);
    }

    @Test
    void distributorListUsesAuthenticatedAdministratorId() {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN);
        allowDistributor();
        when(deviceRepository
                .findByBranch_Distributor_Administrator_Id(
                        16L, pageable()
                ))
                .thenReturn(new PageImpl<>(
                        List.of(device()), pageable(), 1
                ));

        Page<DeviceResponse> result = service.list(EMAIL, 0, 20);

        assertEquals(1L, result.getTotalElements());
        verify(deviceRepository)
                .findByBranch_Distributor_Administrator_Id(
                        16L, pageable()
                );
        verifyNoMoreInteractions(deviceRepository);
    }

    @Test
    void distributorCanReadDeviceWithinOwnDistributor() {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN);
        allowDistributor();
        when(deviceRepository
                .findByIdAndBranch_Distributor_Administrator_Id(
                        1L, 16L
                ))
                .thenReturn(Optional.of(device()));

        assertEquals(1L, service.get(1L, EMAIL).id());

        verify(deviceRepository)
                .findByIdAndBranch_Distributor_Administrator_Id(
                        1L, 16L
                );
        verifyNoMoreInteractions(deviceRepository);
    }

    @Test
    void distributorCannotReadDeviceOutsideOwnDistributor() {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN);
        allowDistributor();

        assertStatus(HttpStatus.NOT_FOUND,
                () -> service.get(99L, EMAIL));

        verify(deviceRepository)
                .findByIdAndBranch_Distributor_Administrator_Id(
                        99L, 16L
                );
        verifyNoMoreInteractions(deviceRepository);
    }

    @Test
    void unlinkedDistributorAdminIsDeniedBeforeDeviceLookup() {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN);

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.list(EMAIL, 0, 20));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.get(1L, EMAIL));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    void inactiveDistributorIsDeniedBeforeDeviceLookup() {
        allowAccount(RoleName.DISTRIBUTOR_ADMIN);
        Distributor distributor = new Distributor();
        distributor.setStatus("INACTIVE");
        when(distributorRepository.findByAdministrator_Id(16L))
                .thenReturn(Optional.of(distributor));

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.list(EMAIL, 0, 20));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.get(1L, EMAIL));

        verifyNoInteractions(deviceRepository);
    }

    @Test
    void companyAdminCanListAllDevicesAndReadById() {
        allowAccount(RoleName.HYKON_ADMIN);
        when(deviceRepository.findAll(pageable()))
                .thenReturn(new PageImpl<>(
                        List.of(device()), pageable(), 1
                ));
        when(deviceRepository.findById(1L))
                .thenReturn(Optional.of(device()));

        assertEquals(1L, service.list(EMAIL, 0, 20).getTotalElements());
        assertEquals(1L, service.get(1L, EMAIL).id());

        verify(deviceRepository).findAll(pageable());
        verify(deviceRepository).findById(1L);
        verifyNoMoreInteractions(deviceRepository);
        verifyNoInteractions(distributorRepository);
    }

    @Test
    void inactiveAccountIsDeniedBeforeAnyDeviceLookup() {
        User account = allowAccount(RoleName.HYKON_ADMIN);
        account.setStatus("INACTIVE");

        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.list(EMAIL, 0, 20));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.get(1L, EMAIL));

        verifyNoInteractions(deviceRepository, distributorRepository);
    }

    @Test
    void missingIdentityIsDeniedWithoutRepositoryAccess() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.list(null, 0, 20));
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.get(1L, " "));

        verifyNoInteractions(
                userRepository, deviceRepository, distributorRepository
        );
    }

    @Test
    void unknownAccountIsDeniedBeforeDeviceLookup() {
        assertStatus(HttpStatus.FORBIDDEN,
                () -> service.list(EMAIL, 0, 20));

        verifyNoInteractions(deviceRepository, distributorRepository);
    }

    @Test
    void invalidPaginationAndDeviceIdsAreRejected() {
        allowAccount(RoleName.USER);

        assertThrows(IllegalArgumentException.class,
                () -> service.list(EMAIL, -1, 20));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(EMAIL, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.list(EMAIL, 0, 101));
        assertThrows(IllegalArgumentException.class,
                () -> service.get(0L, EMAIL));
        assertThrows(IllegalArgumentException.class,
                () -> service.get(null, EMAIL));

        verifyNoInteractions(deviceRepository, distributorRepository);
    }
}