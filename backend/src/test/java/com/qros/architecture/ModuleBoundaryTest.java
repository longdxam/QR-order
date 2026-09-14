package com.qros.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import jakarta.persistence.Entity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.tngtech.archunit.lang.CompositeArchRule.of;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;

@AnalyzeClasses(
        packages = "com.qros",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {
    private static final List<String> MODULES = List.of(
            "identity", "venue", "catalog", "ordering", "payment",
            "inventory", "aigateway", "analytics", "audit");

    @ArchTest
    static final ArchRule shared_khong_phu_thuoc_nghiep_vu =
            noClasses().that().resideInAPackage("com.qros.shared..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(MODULES.stream()
                            .map(module -> "com.qros.%s..".formatted(module))
                            .toArray(String[]::new))
                    .because("kernel phải dùng lại được, không được kéo theo nghiệp vụ");

    @ArchTest
    static final ArchRule ruot_module_la_rieng_tu = moduleInteriorRule();

    @ArchTest
    static final ArchRule transactional_chi_o_service =
            methods().that().areAnnotatedWith(Transactional.class)
                    .should().beDeclaredInClassesThat()
                    .resideInAPackage("com.qros.*.service..")
                    .because("một giao dịch cho mỗi use case, đặt đúng một chỗ");

    @ArchTest
    static final ArchRule entity_khong_ro_ra_api =
            noClasses().that().resideInAPackage("com.qros.*.api..")
                    .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                    .because("api chỉ trao đổi DTO, không trao đổi entity");

    @ArchTest
    static final ArchRule audit_chi_ghi_them =
            classes().that().resideInAPackage("com.qros.audit.repository..")
                    .should().notBeAssignableTo(CrudRepository.class)
                    .because("TM-REP-01: audit chỉ ghi thêm — CrudRepository có sẵn save/delete, "
                            + "kế thừa nó là để hở đúng cánh cửa cần đóng");

    @ArchTest
    static final ArchRule khong_phu_thuoc_vong =
            SlicesRuleDefinition.slices()
                    .matching("com.qros.(*)..").namingSlices("$1")
                    .should().beFreeOfCycles();

    private static ArchRule moduleInteriorRule() {
        ArchRule combinedRule = null;
        for (String module : MODULES) {
            ArchRule moduleRule = moduleInteriorRule(module);
            combinedRule = combinedRule == null ? moduleRule : of(combinedRule).and(moduleRule);
        }
        return combinedRule;
    }

    static ArchRule moduleInteriorRule(String module) {
        return classes().that().resideInAnyPackage(
                        "com.qros.%s.controller..".formatted(module),
                        "com.qros.%s.service..".formatted(module),
                        "com.qros.%s.domain..".formatted(module),
                        "com.qros.%s.repository..".formatted(module))
                .should().onlyBeAccessed().byClassesThat(
                        resideInAPackage("com.qros.shared..")
                                .or(new SamePackageRootPredicate(module)))
                .because("module khác chỉ được gọi qua com.qros.%s.api".formatted(module));
    }
}
