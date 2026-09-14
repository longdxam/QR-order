package com.qros.architecture;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.qros.catalog.domain.FixtureFloatingPointMoney;
import com.qros.catalog.domain.FixtureIntegerMoney;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/**
 * Luật ArchUnit chỉ đáng tin khi có bằng chứng nó bắt được vi phạm. Hôm nay chưa có module
 * nghiệp vụ nào khai báo trường tiền, nên nếu không có phép thử này thì luật xanh mà rỗng.
 */
class MoneyTypeRuleBehaviorTest {

    @Test
    void cho_phep_tien_kieu_long_va_so_thuc_ngoai_tien() {
        JavaClasses classes = new ClassFileImporter().importClasses(FixtureIntegerMoney.class);

        MoneyTypeTest.tien_khong_bao_gio_la_so_thuc.check(classes);
    }

    @Test
    void chan_tien_kieu_double_va_bigdecimal() {
        JavaClasses classes = new ClassFileImporter().importClasses(FixtureFloatingPointMoney.class);

        assertThrows(AssertionError.class,
                () -> MoneyTypeTest.tien_khong_bao_gio_la_so_thuc.check(classes));
    }
}
