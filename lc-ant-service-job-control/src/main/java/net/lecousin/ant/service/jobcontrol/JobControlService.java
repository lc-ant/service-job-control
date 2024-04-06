package net.lecousin.ant.service.jobcontrol;

import java.util.Optional;

import net.lecousin.ant.service.jobcontrol.dto.CleanTasksRequest;
import net.lecousin.ant.service.jobcontrol.dto.JobDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import reactor.core.publisher.Mono;

public interface JobControlService {

	Mono<JobDto> createJob(JobDto job);
	
	Mono<JobDto> getJobByCodeAndParentId(String serviceName, String code, Optional<String> parentId);

	Mono<TaskDto> createTask(TaskDto task);
	
	Mono<TaskDto> createTaskIfNoneForJobId(TaskDto task);
	
	Mono<TaskDto> createTaskIfAllWithSameCodeAndSameJobIdAreDone(TaskDto task);
	
	Mono<Void> taskResult(String serviceName, String taskId, TaskResult result);
	
	Mono<Void> cleanTasks(CleanTasksRequest request);
	
}
