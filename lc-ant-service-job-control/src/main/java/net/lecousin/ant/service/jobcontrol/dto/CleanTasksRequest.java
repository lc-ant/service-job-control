package net.lecousin.ant.service.jobcontrol.dto;

import java.time.Duration;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.lecousin.ant.core.validation.annotations.Mandatory;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanTasksRequest {

	@Mandatory
	private String serviceName;
	@Mandatory
	private String jobId;
	@Mandatory
	private String taskCode;
	private int maxOccurencesToKeep;
	private Duration maxDurationToKeepOccurences;
	
}
