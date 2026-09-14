package com.qros.identity.domain;

import java.util.UUID;

import com.qros.identity.repository.RoleRepository;
import com.qros.identity.repository.UserRoleRepository;
import com.qros.shared.id.UuidV7;

/**
 * Xưởng fixture cho test: {@link AppUser} và {@link UserRole} không có setter, chỉ có constructor
 * gói hẹp trong package — đây là lối vào hợp lệ duy nhất từ test, cùng chỗ với chính entity.
 */
public final class AppUserFixture {

    private AppUserFixture() {
    }

    public static AppUser moi(String email, String passwordHash) {
        return new AppUser(UuidV7.generate(), email, passwordHash, "Người dùng kiểm thử", true);
    }

    /** Gán vai trò {@code BARISTA} tại {@code storeId}, và một vai trò khác toàn tổ chức. */
    public static void ganVaiTro(RoleRepository roleRepository, UserRoleRepository userRoleRepository,
            UUID userId, UUID storeId) {
        ganVaiTro(roleRepository, userRoleRepository, userId, storeId, "BARISTA");
    }

    /** Gán {@code roleCode} tại {@code storeId}, và cùng vai trò đó một lần nữa ở phạm vi toàn tổ chức. */
    public static void ganVaiTro(RoleRepository roleRepository, UserRoleRepository userRoleRepository,
            UUID userId, UUID storeId, String roleCode) {

        UUID roleId = roleRepository.findByCode(roleCode).orElseThrow().getId();
        userRoleRepository.save(new UserRole(UuidV7.generate(), userId, roleId, storeId));
        userRoleRepository.save(new UserRole(UuidV7.generate(), userId, roleId, null));
    }
}
