package net.lecousin.ant.service.jobcontrol;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;
import net.lecousin.ant.core.springboot.utils.ThreadCount;

@ConfigurationProperties(prefix = "lc-ant.job-control")
@Data
public class JobControlProperties {

	private ThreadCount parallelCapacity = ThreadCount.parse("0.5C");
	
}
