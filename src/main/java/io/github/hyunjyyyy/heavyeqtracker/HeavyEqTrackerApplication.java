package io.github.hyunjyyyy.heavyeqtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HeavyEqTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(HeavyEqTrackerApplication.class, args);
	}

}
