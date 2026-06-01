package com.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FmcgShopApplication {

	public static void main(String[] args) {
		SpringApplication.run(FmcgShopApplication.class, args);
	}

}
