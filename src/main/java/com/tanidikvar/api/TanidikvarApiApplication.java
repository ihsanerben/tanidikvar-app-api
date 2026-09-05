package com.tanidikvar.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class TanidikvarApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TanidikvarApiApplication.class, args);
	}

}
