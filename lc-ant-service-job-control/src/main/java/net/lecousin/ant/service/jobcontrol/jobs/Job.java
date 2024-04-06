package net.lecousin.ant.service.jobcontrol.jobs;

import java.io.Serializable;
import java.util.Map;

import net.lecousin.ant.service.jobcontrol.dto.JobDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import reactor.core.publisher.Mono;

public interface Job {

	String getServiceName();
	
	String getCode();
	
	String getDisplayNameNamespace();
	String getDisplayNameKey();
	
	Map<String, Serializable> getDataForCreation(Map<String, Serializable> inputData);
	
	Mono<Void> ensureInstanceIsInitialized(JobDto instance);
	
	Mono<Void> taskDone(String jobId, String taskId, String taskCode, TaskResult result);
	
}
