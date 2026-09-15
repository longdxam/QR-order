package com.qros.identity.api;

import java.util.UUID;

/** Danh tính nhân viên tối thiểu mà module nghiệp vụ được phép sử dụng. */
public record StaffIdentity(UUID userId, String displayName, String roleCode) {
}
