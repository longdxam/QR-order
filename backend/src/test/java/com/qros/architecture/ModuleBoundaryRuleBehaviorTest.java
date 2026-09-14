package com.qros.architecture;

import com.qros.catalog.controller.FixtureCatalogController;
import com.qros.catalog.service.FixtureCatalogService;
import com.qros.ordering.controller.FixtureOrderingController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ModuleBoundaryRuleBehaviorTest {
    @Test
    void cho_phep_truy_cap_noi_bo_cung_module() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                FixtureCatalogService.class,
                FixtureCatalogController.class);

        ModuleBoundaryTest.moduleInteriorRule("catalog").check(classes);
    }

    @Test
    void chan_truy_cap_ruot_cua_module_khac() {
        JavaClasses classes = new ClassFileImporter().importClasses(
                FixtureCatalogService.class,
                FixtureOrderingController.class);

        assertThrows(AssertionError.class,
                () -> ModuleBoundaryTest.moduleInteriorRule("catalog").check(classes));
    }
}
