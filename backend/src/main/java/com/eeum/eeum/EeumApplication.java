package com.eeum.eeum;

import com.eeum.eeum.config.PortOneProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableConfigurationProperties(PortOneProperties.class)
public class EeumApplication {

	public static void main(String[] args) {
		SpringApplication.run(EeumApplication.class, args);
	}

}