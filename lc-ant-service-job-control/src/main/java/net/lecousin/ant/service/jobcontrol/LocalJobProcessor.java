package net.lecousin.ant.service.jobcontrol;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lecousin.ant.core.api.exceptions.ConflictException;
import net.lecousin.ant.core.api.traceability.TraceType;
import net.lecousin.ant.core.api.traceability.Traceability;
import net.lecousin.ant.core.springboot.events.LcAntApplicationReadyEvent;
import net.lecousin.ant.core.springboot.security.ServiceAuthenticationService;
import net.lecousin.ant.core.springboot.serviceregistry.LocalInstanceInfo;
import net.lecousin.ant.core.springboot.traceability.TraceabilityService;
import net.lecousin.ant.core.springboot.utils.SpringContextUtils;
import net.lecousin.ant.service.jobcontrol.annotations.JobControlDeclarationBean;
import net.lecousin.ant.service.jobcontrol.dto.JobDto;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskRequest;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskResponse;
import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import net.lecousin.ant.service.jobcontrol.jobs.Job;
import net.lecousin.ant.service.jobcontrol.jobs.Task;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

@RequiredArgsConstructor
@Slf4j
public class LocalJobProcessor {
	
	private final LocalInstanceInfo localInstanceInfo;
	private final JobControlProperties properties;
	private final JobProcessorService processorService;
	private final JobControlService jobService;
	private final ServiceAuthenticationService authService;
	private final TraceabilityService traceabilityService;
	
	private final Map<String, LocalServiceProcessor> processorsByService = new HashMap<>();
	private List<Mono<Void>> waitingForApplicationReady = new LinkedList<>();
	
	@EventListener
	public void onApplicationReadyEvent(LcAntApplicationReadyEvent event) {
		var beans = event.getApplicationContext().getBeansOfType(JobControlDeclarationBean.class).values();
		log.info("Declaring tasks from {} JobControlDeclaration beans", beans.size());
		beans.stream().flatMap(bean -> bean.getTasks().stream()).forEach(task ->
			declareTask(SpringContextUtils.initBean(event.getApplicationContext(), task, "jobControl_Task_" + task.getServiceName() + "_" + task.getCode())).block()
		);
		log.info("Declaring jobs from {} JobControlDeclaration beans", beans.size());
		beans.stream().flatMap(bean -> bean.getJobs().stream()).forEach(job ->
			declareJob(SpringContextUtils.initBean(event.getApplicationContext(), job, "jobControl_Job_" + job.getServiceName() + "_" + job.getCode())).block()
		);
		log.info("Registering local job processors");
		Flux.concat(processorsByService.values().stream().map(LocalServiceProcessor::register).toList()).collectList().block();
		log.info("Executing waiting requests");
		List<Mono<Void>> monos;
		synchronized (this) {
			monos = waitingForApplicationReady;
			waitingForApplicationReady = null;
		}
		Flux.concat(monos).collectList().block();
		log.info("Creating job instances from {} JobControlDeclaration beans", beans.size());
		beans.stream().flatMap(bean -> bean.getJobInstancesToCreate().stream()).forEach(tuple -> createJobInstance(tuple.getT1(), tuple.getT2(), tuple.getT3()).block());
		log.info("LocalJobProcessor ready.");
	}
	
	@EventListener
	public void onContextClosedEvent(ContextClosedEvent event) {
		log.info("Unregistering local job processors");
		Flux.concat(processorsByService.values().stream().map(LocalServiceProcessor::unregister).toList()).collectList().block();
	}
	
	public Mono<Void> declareJob(Job job) {
		return Mono.defer(() -> {
			log.info("Job declared: service {} code {}", job.getServiceName(), job.getCode());
			LocalServiceProcessor processor;
			synchronized (processorsByService) {
				processor = processorsByService.computeIfAbsent(job.getServiceName(), sn ->  new LocalServiceProcessor(sn, localInstanceInfo.getInstanceId()));
			}
			return processor.declareJob(job);
		});
	}
	
	public Mono<Void> declareTask(Task task) {
		return Mono.defer(() -> {
			log.info("Task declared: service {} code {}", task.getServiceName(), task.getCode());
			LocalServiceProcessor processor;
			synchronized (processorsByService) {
				processor = processorsByService.computeIfAbsent(task.getServiceName(), sn ->  new LocalServiceProcessor(sn, localInstanceInfo.getInstanceId()));
			}
			return processor.declareTask(task);
		});
	}
	
	public Mono<Void> createJobInstance(String serviceName, String jobCode, Map<String, Serializable> jobData) {
		Mono<Void> mono = Mono.defer(() -> {
			log.info("Create job instance: service {} code {}", serviceName, jobCode);
			LocalServiceProcessor processor;
			synchronized (processorsByService) {
				processor = processorsByService.get(serviceName);
			}
			if (processor == null) return Mono.error(new IllegalArgumentException("The job must be declared first"));
			return processor.createJobInstance(jobCode, jobData);
		});
		synchronized (this) {
			if (waitingForApplicationReady != null) {
				CompletableFuture<Void> future = new CompletableFuture<>();
				waitingForApplicationReady.add(mono
					.doOnSuccess(v -> future.complete(null))
					.doOnError(future::completeExceptionally)
				);
				return Mono.fromFuture(future);
			}
		}
		return mono;
	}
	
	@RequiredArgsConstructor
	private class LocalServiceProcessor implements JobProcessor {

		@Getter
		private final String serviceName;
		@Getter
		private final String nodeId;
		
		private Map<String, Job> jobs = new HashMap<>();
		private Map<String, Task> tasks = new HashMap<>();
		
		private Set<String> processingTasksIds = new HashSet<>();
		
		private Mono<Void> register() {
			return authService.executeMonoAs(serviceName, () -> processorService.registerProcessor(this));
		}
		
		private Mono<Void> unregister() {
			return authService.executeMonoAs(serviceName, () -> processorService.unregisterProcessor(this));
		}
		
		private Mono<Void> declareJob(Job job) {
			return Mono.fromRunnable(() -> {
				synchronized (jobs) {
					jobs.put(job.getCode(), job);
				}
			});
		}
		
		private Mono<Void> declareTask(Task task) {
			return Mono.fromRunnable(() -> {
				synchronized (tasks) {
					tasks.put(task.getCode(), task);
				}
			});
		}
		
		private Mono<Void> createJobInstance(String jobCode, Map<String, Serializable> jobData) {
			Job job;
			synchronized (jobs) {
				job = jobs.get(jobCode);
			}
			if (job == null) return Mono.error(new IllegalArgumentException("The job must be declared first"));
			return authService.executeMonoAs(serviceName, () ->
				jobService.createJob(new JobDto(null, null, job.getServiceName(), job.getCode(), job.getDataForCreation(jobData), job.getDisplayNameNamespace(), job.getDisplayNameKey()))
				.onErrorResume(ConflictException.class, error -> jobService.getJobByCodeAndParentId(job.getServiceName(), job.getCode(), Optional.empty()))
				.flatMap(job::ensureInstanceIsInitialized)
			);
		}
		
		@Override
		public int getParallelCapacity() {
			return properties.getParallelCapacity().getCount();
		}
		
		@Override
		public List<String> getPendingTaskIds() {
			List<String> list;
			synchronized (processingTasksIds) {
				list = new ArrayList<>(processingTasksIds);
			}
			return list;
		}
		
		@Override
		public Mono<StartTaskResponse> startTask(StartTaskRequest request) {
			return Mono.fromSupplier(() -> {
				Task task;
				synchronized (tasks) {
					task = tasks.get(request.getTaskCode());
				}
				if (task == null) return new StartTaskResponse(request.getTaskId(), false, "Unknown task");
				synchronized (processingTasksIds) {
					processingTasksIds.add(request.getTaskId());
				}
				Schedulers.parallel().schedule(() -> runTask(task, request));
				return new StartTaskResponse(request.getTaskId(), true, null);
			});
		}
		
		private void runTask(Task task, StartTaskRequest request) {
			Traceability trace = Traceability.createWithCorrelationId(request.getCorrelationId());
			Mono.defer(() -> {
				log.info("Start running task: service {} code {}", task.getServiceName(), task.getCode());
				return authService.executeMonoAs(serviceName, () ->
					traceabilityService.start(serviceName, TraceType.TASK, request.getTaskId() + " - " + request.getTaskCode(),
						task.run(request.getTaskInput())
					)
					.onErrorResume(error -> Mono.just(TaskResult.failed(error)))
					.flatMap(result -> {
						log.info("Task done: service {} code {} status {}", task.getServiceName(), task.getCode(), result.getStatus());
						return jobService.taskResult(task.getServiceName(), request.getTaskId(), result)
						.retryWhen(Retry.backoff(10, Duration.ofSeconds(1)))
						.onErrorResume(error -> {
							// TODO
							return Mono.empty();
						})
						.then(Mono.defer(() -> {
							synchronized (processingTasksIds) {
								processingTasksIds.remove(request.getTaskId());
							}
							Job job;
							synchronized (jobs) {
								job = jobs.get(request.getJobCode());
							}
							if (job == null) return Mono.empty();
							return job.taskDone(request.getJobId(), request.getTaskId(), request.getTaskCode(), result);
						}));
					})
				);
			})
			.contextWrite(trace::toContext)
			.subscribe();
		}
		
	}
	
}
