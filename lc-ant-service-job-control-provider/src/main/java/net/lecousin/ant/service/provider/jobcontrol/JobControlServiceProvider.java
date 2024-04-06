package net.lecousin.ant.service.provider.jobcontrol;

import java.io.Serializable;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.lecousin.ant.connector.database.DatabaseConnector;
import net.lecousin.ant.connector.database.request.FindRequest;
import net.lecousin.ant.core.api.PageRequest;
import net.lecousin.ant.core.api.PageRequest.SortOrder;
import net.lecousin.ant.core.expression.impl.ConditionAnd;
import net.lecousin.ant.core.patch.Patch;
import net.lecousin.ant.core.security.NodePermissionDeclaration;
import net.lecousin.ant.core.security.PermissionDeclaration;
import net.lecousin.ant.core.springboot.aop.Valid;
import net.lecousin.ant.core.springboot.connector.ConnectorService;
import net.lecousin.ant.core.springboot.service.provider.LcAntServiceProvider;
import net.lecousin.ant.core.validation.ValidationContext;
import net.lecousin.ant.service.jobcontrol.JobControlService;
import net.lecousin.ant.service.jobcontrol.dto.CleanTasksRequest;
import net.lecousin.ant.service.jobcontrol.dto.JobDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskDto;
import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import net.lecousin.ant.service.jobcontrol.dto.TaskStatus;
import net.lecousin.ant.service.provider.jobcontrol.db.JobEntity;
import net.lecousin.ant.service.provider.jobcontrol.db.TaskEntity;
import net.lecousin.ant.service.provider.jobcontrol.exceptions.JobNotFoundException;
import reactor.core.publisher.Mono;

@Service("jobControlServiceProvider")
@Primary
@RequiredArgsConstructor
public class JobControlServiceProvider implements JobControlService, LcAntServiceProvider {

	public static final String SERVICE_NAME = "job-control";
	
	private final ConnectorService connectorService;
	
	@Autowired
	@Lazy
	private JobControlScheduler scheduler;
	
	@Override
	public String getServiceName() {
		return SERVICE_NAME;
	}
	
	@Override
	public List<Object> getDependencies() {
		return Collections.emptyList();
	}

	@Override
	public List<PermissionDeclaration> getServicePermissions() {
		return Collections.emptyList();
	}
	
	@Override
	public List<NodePermissionDeclaration> getServiceNodePermissions() {
		return Collections.emptyList();
	}
	
	@Override
	public Mono<Void> init(ConfigurableApplicationContext applicationContext) {
		return scheduler.init();
	}
	
	@Override
	public Mono<Void> stop(ConfigurableApplicationContext applicationContext) {
		return scheduler.stop();
	}
	
	@Override
	@PreAuthorize("principal == 'service:' + #job.serviceName")
	public Mono<JobDto> createJob(@Valid(ValidationContext.CREATION) @P("job") JobDto job) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.create(new JobEntity(
				null,
				Optional.ofNullable(job.getParentId()),
				job.getServiceName(),
				job.getCode(),
				job.getData(),
				job.getDisplayNameNamespace(),
				job.getDisplayNameKey()
			))
		).map(this::toDto);
	}
	
	@Override
	@PreAuthorize("principal == 'service:' + #serviceName")
	public Mono<JobDto> getJobByCodeAndParentId(@P("serviceName") String serviceName, String code, Optional<String> parentId) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.find(JobEntity.class)
			.where(new ConditionAnd(
				JobEntity.FIELD_SERVICE_NAME.is(serviceName),
				JobEntity.FIELD_CODE.is(code),
				JobEntity.FIELD_PARENT_ID.is(parentId)
			))
			.executeSingle()
		).map(this::toDto);
	}
	
	private JobDto toDto(JobEntity entity) {
		return new JobDto(
			entity.getId(),
			entity.getParentId().orElse(null),
			entity.getServiceName(),
			entity.getCode(),
			entity.getData(),
			entity.getDisplayNameNamespace(),
			entity.getDisplayNameKey()
		);
	}
	
	@Override
	@PreAuthorize("principal == 'service:' + #task.serviceName")
	public Mono<TaskDto> createTask(@Valid(ValidationContext.CREATION) @P("task") TaskDto task) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.findById(JobEntity.class, task.getJobId())
			.switchIfEmpty(Mono.error(new JobNotFoundException(task.getJobId())))
			.flatMap(job ->
				db.create(fromDtoForCreation(job, task, true))
			)
		).map(this::toDto);
		// TODO signal new schedule to scheduler
	}
	
	@Override
	@PreAuthorize("principal == 'service:' + #task.serviceName")
	public Mono<TaskDto> createTaskIfNoneForJobId(@Valid(ValidationContext.CREATION) @P("task") TaskDto task) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.findById(JobEntity.class, task.getJobId())
			.switchIfEmpty(Mono.error(new JobNotFoundException(task.getJobId())))
			.flatMap(job ->
				db.find(TaskEntity.class).where(TaskEntity.FIELD_JOB_ID.is(job.getId())).executeSingle()
				.map(found -> true)
				.switchIfEmpty(Mono.just(false))
				.flatMap(taskExists -> {
					if (taskExists.booleanValue()) return Mono.empty();
					return db.create(fromDtoForCreation(job, task, false))
					.flatMap(created ->
						// ensure this is the only one, so we update a single entity with a valid schedule and delete others
						db.patchOne(
							new FindRequest<>(TaskEntity.class).where(TaskEntity.FIELD_JOB_ID.is(job.getId())).paging(PageRequest.of(1).addSort("createdAt", SortOrder.ASC)),
							List.of(Patch.field(TaskEntity.FIELD_SCHEDULE).set(task.getSchedule()))
						).flatMap(goodOne ->
							db.delete(TaskEntity.class, new ConditionAnd(
								TaskEntity.FIELD_JOB_ID.is(job.getId()),
								TaskEntity.FIELD_SCHEDULE.is(Long.MAX_VALUE)
							)).thenReturn(goodOne)
						)
					);
				})
			)
		).map(this::toDto);
		// TODO signal new schedule to scheduler
	}
	
	@Override
	@PreAuthorize("principal == 'service:' + #task.serviceName")
	public Mono<TaskDto> createTaskIfAllWithSameCodeAndSameJobIdAreDone(@Valid(ValidationContext.CREATION) @P("task") TaskDto task) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.findById(JobEntity.class, task.getJobId())
			.switchIfEmpty(Mono.error(new JobNotFoundException(task.getJobId())))
			.flatMap(job ->
				db.findOne(TaskEntity.class, new ConditionAnd(
					TaskEntity.FIELD_JOB_ID.is(job.getId()),
					TaskEntity.FIELD_CODE.is(task.getCode()),
					TaskEntity.FIELD_STATUS.in(List.of(TaskStatus.SCHEDULED, TaskStatus.PROCESSING))
				))
				.map(Optional::of)
				.switchIfEmpty(Mono.just(Optional.empty()))
				.flatMap(found -> {
					if (found.isPresent()) return Mono.empty();
					return db.create(fromDtoForCreation(job, task, false))
					.flatMap(created ->
						// between the search and the creation, another one may be created
						// so we must ensure a single one remains
						// so we patch a single one with a correct schedule and remove others
						db.patchOne(
							new FindRequest<>(TaskEntity.class)
								.where(new ConditionAnd(
									TaskEntity.FIELD_JOB_ID.is(job.getId()),
									TaskEntity.FIELD_CODE.is(task.getCode()),
									TaskEntity.FIELD_STATUS.is(TaskStatus.SCHEDULED)
								))
								.paging(PageRequest.of(1).addSort(TaskEntity.FIELD_CREATED_AT.getNumberField(), SortOrder.ASC)),
							List.of(Patch.field(TaskEntity.FIELD_SCHEDULE).set(task.getSchedule()))
						).flatMap(goodOne ->
							db.delete(TaskEntity.class, new ConditionAnd(
								TaskEntity.FIELD_JOB_ID.is(job.getId()),
								TaskEntity.FIELD_SCHEDULE.is(Long.MAX_VALUE)
							)).thenReturn(goodOne)
						)
					);
				})
			)
		).map(this::toDto);
		// TODO signal new schedule to scheduler
	}
	
	private TaskEntity fromDtoForCreation(JobEntity job, TaskDto dto, boolean setSchedule) {
		return new TaskEntity(
			null, 0L,
			job.getId(),
			job.getCode(),
			job.getServiceName(),
			dto.getCode(),
			dto.getDisplayNameNamespace(),
			dto.getDisplayNameKey(),
			setSchedule ? dto.getSchedule() : Long.MAX_VALUE,
			System.currentTimeMillis(),
			null, null, Optional.empty(), Optional.empty(),
			TaskStatus.SCHEDULED, null,
			dto.getInput(),
			null,
			0, null
		);
	}
	
	private TaskDto toDto(TaskEntity entity) {
		return new TaskDto(
			entity.getId(),
			entity.getJobId(),
			entity.getServiceName(),
			entity.getCode(),
			entity.getInput(),
			entity.getSchedule(),
			entity.getStatus(),
			toTaskResult(entity),
			entity.getDisplayNameNamespace(),
			entity.getDisplayNameKey()
		);
	}
	
	private TaskResult toTaskResult(TaskEntity entity) {
		switch (entity.getStatus()) {
		case SUCCESS: return TaskResult.success(entity.getOutput());
		case FAILED: return TaskResult.failed(entity.getStatusDetails(), entity.getOutput());
		case CANCELED: return TaskResult.canceled(entity.getStatusDetails(), entity.getOutput());
		default: return null;
		}
	}
	
	
	@Override
	@PreAuthorize("principal == 'service:' + #request.serviceName")
	public Mono<Void> cleanTasks(@Valid(ValidationContext.UPDATE) @P("request") CleanTasksRequest request) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			cleanTasksMaxOccurences(db, request.getServiceName(), request.getJobId(), request.getTaskCode(), request.getMaxOccurencesToKeep())
			.then(cleanTasksMaxTime(db, request.getServiceName(), request.getJobId(), request.getTaskCode(), request.getMaxDurationToKeepOccurences()))
		);
	}
	
	private Mono<Void> cleanTasksMaxOccurences(DatabaseConnector db, String serviceName, String jobId, String taskCode, int maxOccurences) {
		if (maxOccurences < 0) return Mono.empty();
		FindRequest<TaskEntity> find = new FindRequest<>(TaskEntity.class);
		find.where(new ConditionAnd(
			TaskEntity.FIELD_SERVICE_NAME.is(serviceName),
			TaskEntity.FIELD_JOB_ID.is(jobId),
			TaskEntity.FIELD_CODE.is(taskCode),
			TaskEntity.FIELD_PROCESSING_END.isNotNull()
		));
		find.paging(PageRequest.of(1, maxOccurences).addSort("processingEnd", SortOrder.DESC));
		return Mono.just(maxOccurences)
		.expand(nb -> {
			if (nb < maxOccurences) return Mono.empty();
			return db.find(find)
			.flatMap(page -> {
				if (page.getData().isEmpty()) return Mono.just(0);
				return db.delete(page.getData()).thenReturn(page.getData().size());
			});
		})
		.then();
	}
	
	private Mono<Void> cleanTasksMaxTime(DatabaseConnector db, String serviceName, String jobId, String taskCode, Duration maxDuration) {
		if (maxDuration == null) return Mono.empty();
		long maxTime = System.currentTimeMillis() - maxDuration.toMillis();
		return db.delete(TaskEntity.class, new ConditionAnd(
			TaskEntity.FIELD_SERVICE_NAME.is(serviceName),
			TaskEntity.FIELD_JOB_ID.is(jobId),
			TaskEntity.FIELD_CODE.is(taskCode),
			TaskEntity.FIELD_PROCESSING_END.lessThan(maxTime)
		));
	}

	
	@Override
	@PreAuthorize("principal == 'service:' + #serviceName")
	public Mono<Void> taskResult(@P("serviceName") String serviceName, String taskId, TaskResult result) {
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.patchOne(
				new FindRequest<>(TaskEntity.class)
				.where(new ConditionAnd(
					TaskEntity.FIELD_SERVICE_NAME.is(serviceName),
					TaskEntity.FIELD_ID.is(taskId)
				)),
				List.of(
					Patch.field(TaskEntity.FIELD_PROCESSING_END).set(System.currentTimeMillis()),
					Patch.field(TaskEntity.FIELD_PROCESSING_NODE_ID).set(null),
					Patch.field(TaskEntity.FIELD_STATUS).set(toTaskStatus(result.getStatus())),
					Patch.field(TaskEntity.FIELD_STATUS_DETAILS).set(result.getMessage()),
					Patch.field(TaskEntity.FIELD_OUTPUT).set((Serializable) result.getOutput())
				)
			)
		).then();
	}
	
	private TaskStatus toTaskStatus(TaskResult.Status status) {
		switch (status) {
		case SUCCESS: return TaskStatus.SUCCESS;
		case CANCELED: return TaskStatus.CANCELED;
		case FAILED: default: return TaskStatus.FAILED;
		}
	}
}
