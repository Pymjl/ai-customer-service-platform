package com.aicsp.biz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.aicsp.biz.mapper")
public class BizServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BizServiceApplication.class, args);
    }
}
