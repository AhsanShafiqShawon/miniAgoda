package com.miniagoda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MiniagodaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniagodaApplication.class, args);
	}

}
