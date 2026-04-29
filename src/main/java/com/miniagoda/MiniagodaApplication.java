package com.miniagoda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class MiniagodaApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniagodaApplication.class, args);
	}

}
