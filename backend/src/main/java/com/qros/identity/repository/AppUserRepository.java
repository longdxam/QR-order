package com.qros.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.qros.identity.domain.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * {@code email} là {@code citext} ở CSDL nên đã không phân biệt hoa thường; không cần
     * {@code IgnoreCase} ở tên phương thức.
     */
    Optional<AppUser> findByEmailAndActiveTrue(String email);
}
