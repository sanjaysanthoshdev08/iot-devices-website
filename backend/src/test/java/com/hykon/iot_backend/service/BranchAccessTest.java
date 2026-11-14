package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.Distributor;
import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.Role;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class BranchAccessTest {

    private User distributorAdmin(long id, boolean overseer) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRole(new Role(RoleName.DISTRIBUTOR_ADMIN));
        user.setAllDistributors(overseer);
        return user;
    }

    private DistributorBranch branchOf(User administrator) {
        Distributor distributor = new Distributor();
        ReflectionTestUtils.setField(distributor, "id", 1L);
        distributor.setAdministrator(administrator);
        DistributorBranch branch = new DistributorBranch();
        branch.setDistributor(distributor);
        return branch;
    }

    @Test
    void overseerManagesEveryBranch() {
        User thrissur = distributorAdmin(5L, false);
        User overseer = distributorAdmin(2L, true);

        assertTrue(overseer.seesAllDistributors());
        assertTrue(BranchAccess.canManage(overseer, branchOf(thrissur)));
    }

    @Test
    void scopedDistributorAdminManagesOnlyTheirOwnDistributor() {
        User thrissur = distributorAdmin(5L, false);
        User ernakulam = distributorAdmin(6L, false);

        assertFalse(thrissur.seesAllDistributors());
        assertTrue(BranchAccess.canManage(thrissur, branchOf(thrissur)));
        assertFalse(BranchAccess.canManage(ernakulam, branchOf(thrissur)));
    }
}
