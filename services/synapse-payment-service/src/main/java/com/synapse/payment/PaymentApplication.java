package com.synapse.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务启动类(Phase 3c)。
 * {@link EnableFeignClients} 调 access 内部视图取金额;{@link EnableScheduling} 供 outbox relay。
 */
@EnableFeignClients
@EnableScheduling
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
