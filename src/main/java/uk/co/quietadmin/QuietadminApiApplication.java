package uk.co.quietadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class QuietadminApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(QuietadminApiApplication.class, args);
	}

}
