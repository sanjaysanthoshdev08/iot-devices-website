package com.hykon.iot_backend.service;

import com.hykon.iot_backend.entity.DistributorBranch;
import com.hykon.iot_backend.entity.RoleName;
import com.hykon.iot_backend.entity.User;

/**
 * Central rule for "may this account manage this branch?".
 * HYKON_ADMIN: any branch. DISTRIBUTOR_ADMIN: branches of their own distributor.
 * BRANCH_ADMIN: only the branch assigned to them. Anyone else: no.
 */
public final class BranchAccess {

    private BranchAccess() {
    }

    public static boolean canManage(User actor, DistributorBranch branch) {
        if (actor == null || actor.getRole() == null
                || actor.getRole().getName() == null || branch == null) {
            return false;
        }
        RoleName role = actor.getRole().getName();
        if (role == RoleName.HYKON_ADMIN) {
            return true;
        }
        if (role == RoleName.DISTRIBUTOR_ADMIN) {
            // The (single) Distributor Admin oversees every distributor and branch.
            return branch.getDistributor() != null;
        }
        if (role == RoleName.BRANCH_ADMIN) {
            return branch.getBranchAdmin() != null
                    && actor.getId().equals(branch.getBranchAdmin().getId());
        }
        return false;
    }
}
