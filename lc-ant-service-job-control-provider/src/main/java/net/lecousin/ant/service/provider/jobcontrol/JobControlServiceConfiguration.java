package net.lecousin.ant.service.provider.jobcontrol;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.lecousin.ant.connector.database.DatabaseConnectorConfiguration;
import net.lecousin.ant.core.springboot.service.provider.LcAntServiceProviderConfiguration;
import net.lecousin.ant.service.jobcontrol.JobControlConfiguration;

@Configuration
@ComponentScan
@Import({
	JobControlConfiguration.class,
	DatabaseConnectorConfiguration.class,
	LcAntServiceProviderConfiguration.class
})
@EnableConfigurationProperties(JobControlProperties.class)
public class JobControlServiceConfiguration {

}
