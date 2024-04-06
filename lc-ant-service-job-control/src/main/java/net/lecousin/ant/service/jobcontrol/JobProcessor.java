package net.lecousin.ant.service.jobcontrol;

import java.util.Collection;

import net.lecousin.ant.service.jobcontrol.dto.StartTaskRequest;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskResponse;
import reactor.core.publisher.Mono;

public interface JobProcessor {
	
	String getServiceName();
	
	String getNodeId();
	
	int getParallelCapacity();
	
	Collection<String> getPendingTaskIds();

	Mono<StartTaskResponse> startTask(StartTaskRequest request);
	
}
