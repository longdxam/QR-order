package com.qros.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Bất biến số 4: mọi lỗi là RFC 7807 kèm {@code code} và {@code traceId}.
 *
 * <p>Bất biến này chỉ kiểm chứng được nếu chỉ có một chỗ dựng thân phản hồi lỗi. Một controller
 * tự ghép {@code ProblemDetail} là một chỗ nữa có thể quên {@code traceId} — và sẽ quên đúng vào
 * lúc cần tra cứu sự cố ({@code TM-OPS-02}, {@code NFR-OBS-01}).
 */
@AnalyzeClasses(packages = "com.qros", importOptions = ImportOption.DoNotIncludeTests.class)
class ProblemResponseTest {

    @ArchTest
    static final ArchRule chi_shared_error_duoc_dung_problemdetail =
            noClasses().that().resideOutsideOfPackage("com.qros.shared.error..")
                    .and().resideOutsideOfPackage("com.qros.generated..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.http.ProblemDetail")
                    .because("phản hồi lỗi chỉ được dựng qua shared/error/ProblemDetailFactory");

    @ArchTest
    static final ArchRule controller_khong_tu_bat_ngoai_le_thanh_phan_hoi =
            noClasses().that().resideInAPackage("com.qros.*.controller..")
                    .should().beAnnotatedWith("org.springframework.web.bind.annotation.ControllerAdvice")
                    .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
                    .because("chỉ có một GlobalExceptionHandler, nằm ở shared/error");
}
