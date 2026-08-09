package com.theo.community_api;

import com.theo.community_api.common.time.UtcDateTimes;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Optional;

@EnableScheduling
@ConfigurationPropertiesScan
@EnableJpaAuditing(dateTimeProviderRef = "utcDateTimeProvider")
@SpringBootApplication
public class CommunityApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CommunityApiApplication.class, args);
	}

	@Bean
	public DateTimeProvider utcDateTimeProvider() {
		return () -> Optional.of(UtcDateTimes.now());
	}

}
