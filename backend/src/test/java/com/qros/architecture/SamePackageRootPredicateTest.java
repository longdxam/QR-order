package com.qros.architecture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SamePackageRootPredicateTest {
    @Test
    void lay_segment_thu_ba_lam_ten_module() {
        assertEquals("ordering", SamePackageRootPredicate.packageRoot("com.qros.ordering.service"));
        assertEquals("shared", SamePackageRootPredicate.packageRoot("com.qros.shared.money"));
        assertEquals("", SamePackageRootPredicate.packageRoot("com.qros"));
    }
}
