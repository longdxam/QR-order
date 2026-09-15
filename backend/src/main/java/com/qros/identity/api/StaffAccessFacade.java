package com.qros.identity.api;

import java.util.Optional;
import java.util.UUID;

/** Cổng kiểm tra quyền có tính đến phạm vi chi nhánh. */
public interface StaffAccessFacade {

    Optional<StaffIdentity> findAuthorized(UUID userId, UUID storeId, String permission);
}
