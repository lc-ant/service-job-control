package net.lecousin.ant.service.jobcontrol;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.lecousin.ant.core.springboot.security.ServiceAuthenticationService;
import net.lecousin.ant.core.springboot.serviceregistry.LocalInstanceInfo;
import net.lecousin.ant.core.springboot.traceability.TraceabilityService;
import net.lecousin.ant.service.jobcontrol.annotations.JobControlAnnotationsPostProcessor;

@Configuration
@EnableConfigurationProperties(JobControlProperties.class)
public class JobControlConfiguration {
	
	@Bean
	LocalJobProcessor localJobProcessor(
		LocalInstanceInfo localInstanceInfo, JobControlProperties properties, JobProcessorService processorService, JobControlService jobService,
		ServiceAuthenticationService authService, TraceabilityService traceabilityService
	) {
		return new LocalJobProcessor(localInstanceInfo, properties, processorService, jobService, authService, traceabilityService);
	}

	@Bean
	JobControlAnnotationsPostProcessor jobControlAnnotationsPostProcessor() {
		return new JobControlAnnotationsPostProcessor();
	}
	
}
