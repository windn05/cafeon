package com.example.docksystem_erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@EntityScan("com.example.docksystem_erp.entity")
public class DocksystemErpApplication {

	public static void main(String[] args) {
		SpringApplication.run(DocksystemErpApplication.class, args);
	}

}
