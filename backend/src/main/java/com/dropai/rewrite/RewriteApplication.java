package com.dropai.rewrite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@MapperScan("com.dropai.rewrite.mapper")
@SpringBootApplication
public class RewriteApplication {

    public static void main(String[] args) {
        SpringApplication.run(RewriteApplication.class, args);
    }
}
