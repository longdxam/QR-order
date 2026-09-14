package com.qros.identity.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.qros.identity.domain.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserId(UUID userId);

    /** Dùng để tính claim {@code mfaBlocked} lúc đăng nhập — {@code FR-AUTH-02}. */
    @Query("SELECT r.code FROM UserRole ur JOIN Role r ON r.id = ur.roleId WHERE ur.userId = :userId")
    List<String> findRoleCodesByUserId(@Param("userId") UUID userId);
}
