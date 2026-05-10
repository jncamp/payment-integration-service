package com.example.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.example.payment",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.example\\.payment\\.exception\\..*"
        )
)
public class PaymentIntegrationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentIntegrationServiceApplication.class, args);
    }
}
