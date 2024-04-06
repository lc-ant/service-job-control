package net.lecousin.ant.service.jobcontrol.jobs;

import java.io.Serializable;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;

import net.lecousin.ant.service.jobcontrol.JobControlService;
import net.lecousin.ant.service.jobcontrol.dto.CleanTasksRequest;
import net.lecousin.ant.service.jobcontrol.dto.JobDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import reactor.core.publisher.Mono;

public abstract class RecurringTask implements Job {

	@Autowired
	protected JobControlService jobService;

	public abstract Duration getInitialTaskDelay();
	
	public abstract Duration getTaskInterval();
	
	public abstract int getMaxOccurencesToKeep();
	
	public abstract Duration getMaxDurationToKeepOccurences();
	
	public String getTaskCode() {
		return getCode();
	}
	
	public Map<String, Serializable> getTaskInput() {
		return Map.of();
	}
	
	@Override
	public Map<String, Serializable> getDataForCreation(Map<String, Serializable> inputData) {
		return inputData;
	}

	@Override
	public Mono<Void> ensureInstanceIsInitialized(JobDto instance) {
		return jobService.createTaskIfNoneForJobId(createNextTask(instance.getId(), getInitialTaskDelay()))
		.switchIfEmpty(Mono.defer(() -> jobService.createTaskIfAllWithSameCodeAndSameJobIdAreDone(createNextTask(instance.getId(), getTaskInterval()))))
		.then();
	}
	
	private TaskDto createNextTask(String jobId, Duration delay) {
		return new TaskDto(
			null,
			jobId,
			getServiceName(),
			getTaskCode(),
			getTaskInput(),
			System.currentTimeMillis() + delay.toMillis(),
			null,
			null,
			getDisplayNameNamespace(),
			getDisplayNameKey()
		);
	}
	
	@Override
	public Mono<Void> taskDone(String jobId, String taskId, String taskCode, TaskResult result) {
		return jobService.createTask(createNextTask(jobId, getTaskInterval()))
		.then(jobService.cleanTasks(new CleanTasksRequest(getServiceName(), jobId, taskCode, getMaxOccurencesToKeep(), getMaxDurationToKeepOccurences())));
	}
	
}
