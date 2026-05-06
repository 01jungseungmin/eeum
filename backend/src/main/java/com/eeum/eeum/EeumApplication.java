package com.eeum.eeum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class EeumApplication {

	public static void main(String[] args) {
		SpringApplication.run(EeumApplication.class, args);
	}

}