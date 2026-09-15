package com.qros.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.identity.domain.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    interface StaffGrantProjection {
        String getDisplayName();

        String getRoleCode();
    }

    List<UserRole> findByUserId(UUID userId);

    /** Dùng để tính claim {@code mfaBlocked} lúc đăng nhập — {@code FR-AUTH-02}. */
    @Query("SELECT r.code FROM UserRole ur JOIN Role r ON r.id = ur.roleId WHERE ur.userId = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") UUID userId);

    @Query(value = """
            SELECT DISTINCT rp.permission
            FROM user_role ur
            JOIN role_permission rp ON rp.role_id = ur.role_id
            WHERE ur.user_id = :userId
            ORDER BY rp.permission
            """, nativeQuery = true)
    List<String> findPermissionsByUserId(@Param("userId") UUID userId);

    @Query(value = """
            SELECT u.display_name AS displayName, r.code AS roleCode
            FROM app_user u
            JOIN user_role ur ON ur.user_id = u.id
            JOIN role r ON r.id = ur.role_id
            JOIN role_permission rp ON rp.role_id = r.id
            WHERE u.id = :userId
              AND u.active = true
              AND (ur.store_id = :storeId OR ur.store_id IS NULL)
              AND rp.permission = :permission
            ORDER BY CASE WHEN ur.store_id IS NULL THEN 1 ELSE 0 END
            LIMIT 1
            """, nativeQuery = true)
    java.util.Optional<StaffGrantProjection> findGrant(
            @Param("userId") UUID userId,
            @Param("storeId") UUID storeId,
            @Param("permission") String permission);
}
