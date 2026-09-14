package com.qros.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.qros")
class NoRawSqlTest {
    @ArchTest
    static final ArchRule khong_dung_jdbc_tho =
            noClasses().should().accessClassesThat()
                    .resideInAnyPackage("java.sql..")
                    .because("chỉ dùng truy vấn tham số hoá qua JPA hoặc jOOQ");

    @ArchTest
    static final ArchRule chi_repository_duoc_dung_entitymanager =
            noClasses().that().resideOutsideOfPackage("com.qros.*.repository..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("jakarta.persistence.EntityManager");
}
