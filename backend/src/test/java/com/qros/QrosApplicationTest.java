package com.qros;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QrosApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void frAuth01_testProfileStartsApplicationContext() {
        assertThat(applicationContext.getBean(QrosApplication.class)).isNotNull();
    }
}
