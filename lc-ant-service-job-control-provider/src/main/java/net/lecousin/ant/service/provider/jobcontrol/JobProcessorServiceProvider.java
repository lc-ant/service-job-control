package net.lecousin.ant.service.provider.jobcontrol;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import net.lecousin.ant.connector.database.DatabaseConnector;
import net.lecousin.ant.core.expression.impl.ConditionAnd;
import net.lecousin.ant.core.patch.Patch;
import net.lecousin.ant.core.springboot.connector.ConnectorService;
import net.lecousin.ant.service.jobcontrol.JobProcessor;
import net.lecousin.ant.service.jobcontrol.JobProcessorService;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskRequest;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskResponse;
import net.lecousin.ant.service.provider.jobcontrol.db.JobProcessorEntity;
import net.lecousin.ant.service.provider.jobcontrol.db.TaskEntity;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
@RequiredArgsConstructor
public class JobProcessorServiceProvider implements JobProcessorService {

	private final ConnectorService connectorService;
	
	private final List<Tuple3<JobProcessor, String, Long>> connectedProcessors = new LinkedList<>();
	
	// TODO clean JobProcessorEntity with old lastSeen and connected = false
	
	@Override
	public Mono<Void> registerProcessor(JobProcessor processor) {
		if (StringUtils.isBlank(processor.getServiceName()) || StringUtils.isBlank(processor.getNodeId()))
			return Mono.error(new IllegalArgumentException("service name and node id must not be blank"));
		return connectorService.getConnector(DatabaseConnector.class)
		.flatMap(db ->
			db.findOne(JobProcessorEntity.class, new ConditionAnd(
				JobProcessorEntity.FIELD_SERVICE_NAME.is(processor.getServiceName()),
				JobProcessorEntity.FIELD_NODE_ID.is(processor.getNodeId())
			))
			.flatMap(entity -> {
				entity.setLastSeen(System.currentTimeMillis());
				entity.setParallelCapacity(processor.getParallelCapacity());
				entity.setProcessingTasks(new LinkedList<>(processor.getPendingTaskIds()));
				return db.update(entity);
			})
			.switchIfEmpty(Mono.defer(() -> {
				JobProcessorEntity entity = new JobProcessorEntity();
				entity.setServiceName(processor.getServiceName());
				entity.setNodeId(processor.getNodeId());
				entity.setLastSeen(System.currentTimeMillis());
				entity.setParallelCapacity(processor.getParallelCapacity());
				entity.setProcessingTasks(new LinkedList<>(processor.getPendingTaskIds()));
				return db.save(entity);
			}))
		)
		.doOnNext(entity -> {
			synchronized (connectedProcessors) {
				connectedProcessors.add(Tuples.of(processor, entity.getId(), entity.getLastSeen()));
				// TODO signal new processor -> may be new schedule
			}
		})
		.then();
	}
	
	@Override
	public Mono<Void> unregisterProcessor(JobProcessor processor) {
		return Mono.fromSupplier(() -> {
			synchronized (connectedProcessors) {
				var opt = connectedProcessors.stream().filter(tuple -> tuple.getT1() == processor).findAny();
				if (opt.isPresent()) {
					connectedProcessors.remove(opt.get());
					return opt.get();
				}
				return null;
			}
		})
		.flatMap(tuple -> 
			connectorService.getConnector(DatabaseConnector.class)
			.flatMap(db -> db.patchOne(
				JobProcessorEntity.class,
				new ConditionAnd(
					JobProcessorEntity.FIELD_ID.is(tuple.getT2()),
					JobProcessorEntity.FIELD_LAST_SEEN.is(tuple.getT3())
				),
				List.of(
					Patch.field(JobProcessorEntity.FIELD_LAST_SEEN).set(System.currentTimeMillis())
				)
			))
		).then();
	}
	
	Mono<List<String>> getConnectedProcessorsIds() {
		return Mono.fromSupplier(() -> {
			synchronized (connectedProcessors) {
				return connectedProcessors.stream().map(Tuple2::getT2).toList();
			}
		});
	}
	
	private JobProcessor getProcessorById(String id) {
		synchronized (connectedProcessors) {
			return connectedProcessors.stream().filter(tuple -> tuple.getT2().equals(id)).findAny().map(Tuple2::getT1).orElse(null);
		}
	}
	
	Mono<StartTaskResponse> startTask(JobProcessorEntity processorEntity, TaskEntity taskEntity) {
		return Mono.defer(() -> {
			JobProcessor processor = getProcessorById(processorEntity.getId());
			if (processor == null) return Mono.just(new StartTaskResponse(taskEntity.getId(), false, null));
			return processor.startTask(new StartTaskRequest(taskEntity.getCode(), taskEntity.getId(), taskEntity.getInput(), taskEntity.getJobId(), taskEntity.getJobCode(), taskEntity.getCorrelationId().get()));
		});
	}
	
}
