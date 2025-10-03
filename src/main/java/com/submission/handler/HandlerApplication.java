package com.submission.handler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages = {
    "com.submission.handler",
    "com.service",
    "com.util",
    "com.model",
    "com.config"
})
public class HandlerApplication {

	public static void main(String[] args) {
		SpringApplication.run(HandlerApplication.class, args);
	}

}
