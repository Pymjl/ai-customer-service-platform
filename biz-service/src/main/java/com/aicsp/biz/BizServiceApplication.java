package com.aicsp.biz;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(basePackages = "com.aicsp.biz.mapper", annotationClass = Mapper.class)
public class BizServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BizServiceApplication.class, args);
    }
}
