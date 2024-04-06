package net.lecousin.ant.service.provider.jobcontrol;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "lc-ant.service.job-control")
public class JobControlProperties {

	Duration schedulerInterval = Duration.ofMinutes(2);
	Duration schedulerMinimumInterval = Duration.ofMillis(100);
	Duration schedulerInitialDelay = Duration.ofSeconds(10);
	
	Duration maxTaskReservationTime = Duration.ofSeconds(90);
	
}
