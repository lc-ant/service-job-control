package net.lecousin.ant.service.provider.jobcontrol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.lecousin.ant.connector.database.DatabaseConnector;
import net.lecousin.ant.connector.database.request.FindRequest;
import net.lecousin.ant.core.api.PageRequest;
import net.lecousin.ant.core.api.PageRequest.SortOrder;
import net.lecousin.ant.core.expression.impl.ConditionAnd;
import net.lecousin.ant.core.api.PageResponse;
import net.lecousin.ant.core.api.traceability.Traceability;
import net.lecousin.ant.core.patch.Patch;
import net.lecousin.ant.core.springboot.connector.ConnectorService;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskResponse;
import net.lecousin.ant.service.jobcontrol.dto.TaskStatus;
import net.lecousin.ant.service.provider.jobcontrol.db.JobProcessorEntity;
import net.lecousin.ant.service.provider.jobcontrol.db.TaskEntity;
import net.lecousin.commons.reactive.MonoUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
public class JobControlScheduler {
	
	private final JobControlProperties properties;
	private final ConnectorService connectorService;
	
	@Autowired
	@Lazy
	private JobProcessorServiceProvider processorService;
	
	private final Object nextScheduleLock = new Object();
	private Disposable nextSchedule = null;
	private boolean running = false;
	private CompletableFuture<Void> stopRequested = null;
	private boolean stopped = false;

	Mono<Void> init() {
		scheduleIn(properties.getSchedulerInitialDelay().toMillis(), false);
		return Mono.empty();
	}
	
	Mono<Void> stop() {
		synchronized (nextScheduleLock) {
			if (stopped) return Mono.empty();
			if (nextSchedule != null) {
				nextSchedule.dispose();
				nextSchedule = null;
			}
			if (stopRequested == null) {
				stopRequested = new CompletableFuture<>();
				if (!running) {
					stopped = true;
					stopRequested.complete(null);
				}
			}
		}
		return Mono.fromFuture(stopRequested);
	}
	
	private void scheduleIn(long delay, boolean fromRun) {
		synchronized (nextScheduleLock) {
			if (running && !fromRun) return;
			if (stopRequested != null) return;
			if (nextSchedule != null)
				nextSchedule.dispose();
			nextSchedule = Schedulers.parallel().schedule(this::run, delay, TimeUnit.MILLISECONDS);
		}
	}

	private void run() {
		synchronized (nextScheduleLock) {
			if (stopRequested != null) return;
			if (nextSchedule != null)
				nextSchedule.dispose();
			running = true;
			nextSchedule = null;
		}
		releaseLockedTasks()
		.then(Mono.defer(() -> stopRequested != null ? Mono.empty() :
			processNextTasks()
			.flatMap(nextScheduleTime -> {
				long delay = properties.getSchedulerInterval().toMillis();
				if (nextScheduleTime != 0 && nextScheduleTime < System.currentTimeMillis() + delay)
					delay = Math.max(nextScheduleTime - System.currentTimeMillis(), properties.getSchedulerMinimumInterval().toMillis());
				scheduleIn(delay, true);
				return Mono.empty();
			})
		))
		.doFinally(s -> {
			synchronized (nextScheduleLock) {
				running = false;
				if (stopRequested != null) {
					stopped = true;
					stopRequested.complete(null);
				}
			}
		})
		.subscribe();
	}
	
	private Mono<Void> releaseLockedTasks() {
		return getDb().flatMap(db ->
			db.patchManyNonAtomic(
				new FindRequest<>(TaskEntity.class)
				.where(TaskEntity.FIELD_RESERVATION_TIME.lessThan(System.currentTimeMillis() - properties.getMaxTaskReservationTime().toMillis())),
				List.of(Patch.field(TaskEntity.FIELD_RESERVATION_TIME).set(null)))
			.then()
		);
	}
	
	private Mono<Long> processNextTasks() {
		// for all connected processors
		return MonoUtils.zipParallel(getConnectedProcessors(), getDb())
		.flatMap(processorsDb -> {
			List<String> processorsIds = processorsDb.getT1();
			DatabaseConnector db = processorsDb.getT2();
			// that are available for processing
			return getProcessorsAvailableAmong(db, processorsIds)
			.flatMapMany(processors -> {
				// group them by serviceName
				Map<String, List<JobProcessorEntity>> processorsByService = new HashMap<>();
				processors.forEach(processor ->
					processorsByService.computeIfAbsent(processor.getServiceName(), k -> new LinkedList<>())
					.add(processor)
				);
				return Flux.fromIterable(processorsByService.values());
			})
			.flatMap(serviceAvailableProcessors -> {
				int limit = getProcessorsAvailableSlots(serviceAvailableProcessors);
				String serviceName = serviceAvailableProcessors.get(0).getServiceName();
				return reserveTasksToBeProcessedForService(db, serviceName, limit)
				.flatMap(tasks ->
					processServiceTasks(db, serviceAvailableProcessors, tasks)
					.thenReturn(tasks.size() - limit)
				)
				.flatMap(nbRemaining -> nbRemaining > 0 ? getNextSchedule(db, serviceName) : Mono.just(0L));
			})
			.reduce((previousValue, newValue) -> previousValue == 0 ? newValue : (newValue == 0 ? previousValue : Math.min(previousValue, newValue)));
		})
		.switchIfEmpty(Mono.just(0L));
	}
	
	private Mono<DatabaseConnector> getDb() {
		return connectorService.getConnector(DatabaseConnector.class);
	}
	
	private Mono<List<String>> getConnectedProcessors() {
		return processorService.getConnectedProcessorsIds();
	}
	
	private Mono<List<JobProcessorEntity>> getProcessorsAvailableAmong(DatabaseConnector db, List<String> ids) {
		return db.find(JobProcessorEntity.class)
		.where(new ConditionAnd(
			JobProcessorEntity.FIELD_PARALLEL_CAPACITY.greaterThan(JobProcessorEntity.FIELD_PROCESSING_TASKS.size()),
			JobProcessorEntity.FIELD_ID.in(new LinkedList<>(ids))
		))
		.execute()
		.map(PageResponse::getData);
	}
	
	private Mono<List<TaskEntity>> reserveTasksToBeProcessedForService(DatabaseConnector db, String serviceName, int limit) {
		long now = System.currentTimeMillis();
		return db.patchManyAtomic(
			new FindRequest<>(TaskEntity.class)
			.where(new ConditionAnd(
				TaskEntity.FIELD_SCHEDULE.lessOrEqualTo(now),
				TaskEntity.FIELD_PROCESSING_START.isNull(),
				TaskEntity.FIELD_RESERVATION_TIME.isNull(),
				TaskEntity.FIELD_SERVICE_NAME.is(serviceName)
			))
			.paging(PageRequest.of(limit).addSort("lastReservationTime", PageRequest.SortOrder.ASC))
			,List.of(
				Patch.field(TaskEntity.FIELD_RESERVATION_TIME).set(now),
				Patch.field(TaskEntity.FIELD_LAST_RESERVATION_TIME).set(now)
			)
		);
	}

	private int getProcessorsAvailableSlots(List<JobProcessorEntity> processors) {
		return processors.stream().reduce(0, (nb, processor) -> nb + processor.getParallelCapacity() - processor.getProcessingTasks().size(), (a,b) -> a + b);
	}
	
	private Mono<Long> getNextSchedule(DatabaseConnector db, String serviceName) {
		return db.find(TaskEntity.class)
		.where(new ConditionAnd(
			TaskEntity.FIELD_PROCESSING_START.isNull(),
			TaskEntity.FIELD_RESERVATION_TIME.isNull(),
			TaskEntity.FIELD_SERVICE_NAME.is(serviceName)
		))
		.paging(PageRequest.first().addSort("schedule", SortOrder.ASC))
		.executeSingle()
		.map(task -> task.getSchedule())
		.switchIfEmpty(Mono.just(0L));
	}
	
	private Mono<Void> processServiceTasks(DatabaseConnector db, List<JobProcessorEntity> processors, List<TaskEntity> tasks) {
		return Flux.fromIterable(tasks)
		.flatMap(task -> sendToProcessor(db, task, processors))
		.then();
	}

	private static final Comparator<JobProcessorEntity> PROCESSOR_COMPARATOR = (p1, p2) ->
		Integer.valueOf(p2.getProcessingTasks().size() - p2.getParallelCapacity()).compareTo(
			Integer.valueOf(p1.getProcessingTasks().size() - p1.getParallelCapacity()));
	
	private Mono<Void> sendToProcessor(DatabaseConnector db, TaskEntity task, List<JobProcessorEntity> processors) {
		List<JobProcessorEntity> list = new LinkedList<>(processors);
		list.sort(PROCESSOR_COMPARATOR);
		JobProcessorEntity first = list.removeFirst();
		return sendToProcessor(db, task, first)
		.flatMap(response -> {
			if (response.isAccepted()) return Mono.empty();
			if (response.getFatalErrorMessage() != null) return releaseTask(db, task, getPatchForTaskError(response.getFatalErrorMessage()));
			if (list.isEmpty()) return releaseTask(db, task, List.of());
			return sendToProcessor(db, task, processors);
		});
	}
	
	private Mono<StartTaskResponse> sendToProcessor(DatabaseConnector db, TaskEntity task, JobProcessorEntity processor) {
		return db.patchOne(
			processor,
			JobProcessorEntity.FIELD_PARALLEL_CAPACITY.greaterThan(JobProcessorEntity.FIELD_PROCESSING_TASKS.size()),
			List.of(Patch.field(JobProcessorEntity.FIELD_PROCESSING_TASKS).appendElement(task.getId()))
		)
		.flatMap(processorReserved -> {
			Traceability trace = Traceability.create();
			return db.patchOne(
				task,
				List.of(
					Patch.field(TaskEntity.FIELD_PROCESSING_START).set(System.currentTimeMillis()),
					Patch.field(TaskEntity.FIELD_PROCESSING_NODE_ID).set(processorReserved.getNodeId()),
					Patch.field(TaskEntity.FIELD_RESERVATION_TIME).set(null),
					Patch.field(TaskEntity.FIELD_CORRELATION_ID).set(trace.getCorrelationId())
				)
			)
			.flatMap(releasedTask -> sendMessageProcessorStartTask(db, processorReserved, releasedTask));
		})
		.switchIfEmpty(Mono.just(new StartTaskResponse(task.getId(), false, null)));
	}
	
	private Collection<Patch> getPatchForTaskError(String errorMessage) {
		return List.of(
			Patch.field(TaskEntity.FIELD_PROCESSING_END).set(System.currentTimeMillis()),
			Patch.field(TaskEntity.FIELD_STATUS).set(TaskStatus.FAILED),
			Patch.field(TaskEntity.FIELD_STATUS_DETAILS).set(errorMessage)
		);
	}
	
	private Mono<Void> releaseTask(DatabaseConnector db, TaskEntity task, Collection<Patch> additionalPatch) {
		List<Patch> patch = new ArrayList<>(additionalPatch.size() + 1);
		patch.addAll(additionalPatch);
		patch.add(Patch.field(TaskEntity.FIELD_RESERVATION_TIME).set(null));
		return db.patchOne(task, patch).then();
	}
	
	private Mono<StartTaskResponse> sendMessageProcessorStartTask(DatabaseConnector db, JobProcessorEntity processor, TaskEntity task) {
		return processorService.startTask(processor, task)
		.onErrorResume(error -> Mono.just(new StartTaskResponse(task.getId(), false, error.getMessage())))
		.flatMap(response -> {
			if (response.isAccepted()) return Mono.just(response);
			return db.patchOne(processor, List.of(Patch.field(JobProcessorEntity.FIELD_PROCESSING_TASKS).removeElement(task.getId())))
			.then(db.patchOne(task, List.of(
				Patch.field(TaskEntity.FIELD_PROCESSING_START).set(null),
				Patch.field(TaskEntity.FIELD_PROCESSING_NODE_ID).set(null)
			)))
			.thenReturn(response);
		});
	}

}
