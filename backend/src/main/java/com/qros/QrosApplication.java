package com.qros;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QrosApplication {

    public static void main(String[] args) {
        // Chuẩn hoá thời gian hạ tầng; timezone nghiệp vụ được lấy từ từng chi nhánh.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(QrosApplication.class, args);
    }
}
