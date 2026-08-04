package com.jonnathangarcia.conservationsync;

import org.springframework.boot.SpringApplication;

public class TestConservationProgramSyncLabApplication {

	public static void main(String[] args) {
		SpringApplication.from(ConservationProgramSyncLabApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
