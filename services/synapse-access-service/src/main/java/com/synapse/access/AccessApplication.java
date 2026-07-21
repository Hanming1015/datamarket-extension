package com.synapse.access;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 访问申请编排服务启动类(Phase 3a)。
 * {@link EnableFeignClients} 开启声明式 RPC:编排时 Feign 调 consent(匹配)/ dataset(报价)。
 */
@EnableFeignClients
@SpringBootApplication
public class AccessApplication {
    public static void main(String[] args) {
        SpringApplication.run(AccessApplication.class, args);
    }
}
