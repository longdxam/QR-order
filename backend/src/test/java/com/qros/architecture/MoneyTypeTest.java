package com.qros.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import java.math.BigDecimal;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Bất biến số 2: tiền là {@code long} đơn vị đồng ({@code FR-CUS-08}).
 *
 * <p>Một trường {@code double} lọt vào giữa chuỗi tính tổng thì sai số chỉ hiện ra ở hoá đơn thật,
 * sau khi đã in cho khách. Chặn theo tên trường vì đó là chỗ tiền thực sự nằm; các đại lượng
 * không phải tiền như tồn kho ({@code numeric(12,3)}) hay chi phí AI theo USD vẫn được dùng
 * {@link BigDecimal}.
 */
@AnalyzeClasses(packages = "com.qros", importOptions = ImportOption.DoNotIncludeTests.class)
class MoneyTypeTest {

    private static final String TEN_TRUONG_TIEN =
            ".*(?i)(price|amount|total|subtotal|discount|surcharge|fee|balance).*";

    @ArchTest
    static final ArchRule tien_khong_bao_gio_la_so_thuc =
            noFields().that().haveNameMatching(TEN_TRUONG_TIEN)
                    .and().areDeclaredInClassesThat().resideOutsideOfPackage("com.qros.generated..")
                    .should().haveRawType(double.class)
                    .orShould().haveRawType(float.class)
                    .orShould().haveRawType(Double.class)
                    .orShould().haveRawType(Float.class)
                    .orShould().haveRawType(BigDecimal.class)
                    .because("tiền là long đơn vị đồng, dùng shared/money/Money");
}
