package com.pheme.phemenotify;

import org.springframework.boot.SpringApplication;

public class TestPhemeNotifyApplication {

    public static void main(String[] args) {
        SpringApplication.from(PhemeNotifyApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
    }
}
