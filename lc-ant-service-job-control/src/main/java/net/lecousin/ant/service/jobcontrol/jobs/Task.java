package net.lecousin.ant.service.jobcontrol.jobs;

import java.io.Serializable;
import java.util.Map;

import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import reactor.core.publisher.Mono;

public interface Task {

	String getServiceName();
	
	String getCode();
	
	Mono<TaskResult> run(Map<String, Serializable> taskInput);
}
