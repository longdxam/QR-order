package com.qros.identity.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.qros.identity.api.StaffAccessFacade;
import com.qros.identity.api.StaffIdentity;
import com.qros.identity.repository.UserRoleRepository;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class StaffAccessFacadeImpl implements StaffAccessFacade {

    private final UserRoleRepository userRoleRepository;

    public StaffAccessFacadeImpl(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffIdentity> findAuthorized(UUID userId, UUID storeId, String permission) {
        return userRoleRepository.findGrant(userId, storeId, permission)
                .map(grant -> new StaffIdentity(userId, grant.getDisplayName(), grant.getRoleCode()));
    }
}
