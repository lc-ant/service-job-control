package net.lecousin.ant.service.client.jobcontrol;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lecousin.ant.core.security.LcAntSecurity;
import net.lecousin.ant.core.springboot.security.SecurityUtils;
import net.lecousin.ant.core.springboot.security.ServiceAuthenticationService;
import net.lecousin.ant.core.springboot.service.client.websocket.WebSocketClientAutoReconnection;
import net.lecousin.ant.core.springboot.service.client.websocket.WebSocketClientConnection;
import net.lecousin.ant.core.springboot.service.client.websocket.WebSocketClientService;
import net.lecousin.ant.service.jobcontrol.JobProcessor;
import net.lecousin.ant.service.jobcontrol.JobProcessorService;
import net.lecousin.ant.service.jobcontrol.dto.JobProcessorDeclaration;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskRequest;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskResponse;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
public class JobProcessorClient implements JobProcessorService {
	
	private static final String KEY_PROCESSOR = "job-processor";
	
	@Autowired
	private ServiceAuthenticationService authService;
	
	@Autowired
	private WebSocketClientService webSocketClientService;
	
	@Autowired
	private ReactiveDiscoveryClient discoveryClient;
	
	@Value("${lc-ant.job-control.client.auto-reconnect-every:10s}")
	private Duration reconnectEvery;
	
	@Value("${lc-ant.job-control.client.ping-interval:30s}")
	private Duration pingInterval;
	
	@Value("${lc-ant.job-control.client.check-instances-interval:5m}")
	private Duration checkJobControlInstancesInterval;
	
	@Value("${lc-ant.services.job-control:job-control}")
	private String jobControlServiceName;
	
	private final Map<JobProcessor, JobProcessorConnection> registeredProcessors = new HashMap<>();

	@Override
	public Mono<Void> registerProcessor(JobProcessor processor) {
		return ReactiveSecurityContextHolder.getContext()
		.flatMap(auth -> {
			if (auth.getAuthentication() != null && SecurityUtils.isSubject(auth.getAuthentication(), LcAntSecurity.SUBJECT_TYPE_SERVICE, processor.getServiceName()))
				return Mono.just(auth.getAuthentication());
			return Mono.empty();
		})
		.flatMap(auth -> register(processor).thenReturn(0))
		.switchIfEmpty(Mono.defer(() -> authService.executeMonoAs(processor.getServiceName(), () -> register(processor).thenReturn(0))))
		.then();
	}
	
	private Mono<Void> register(JobProcessor processor) {
		JobProcessorConnection jpc = new JobProcessorConnection(processor);
		return jpc.start()
		.then(Mono.fromRunnable(() -> {
			synchronized (registeredProcessors) {
				registeredProcessors.put(processor, jpc);
			}
		}));
	}
	
	@Override
	public Mono<Void> unregisterProcessor(JobProcessor processor) {
		JobProcessorConnection jpc;
		synchronized (registeredProcessors) {
			jpc = registeredProcessors.remove(processor);
		}
		if (jpc == null) return Mono.empty();
		return jpc.stop();
	}
	
	@RequiredArgsConstructor
	private class JobProcessorConnection {
		private final JobProcessor processor;
		private final Map<URI, Mono<WebSocketClientAutoReconnection>> connections = new HashMap<>();
		
		private boolean stopRequested = false;
		private Disposable nextCheckSchedule = null;
		private boolean checking = false;
		private CompletableFuture<Void> stopped = new CompletableFuture<>();
		
		public Mono<Void> start() {
			return checkInstances();
		}
		
		public Mono<Void> stop() {
			synchronized (this) {
				stopRequested = true;
				if (nextCheckSchedule != null) nextCheckSchedule.dispose();
				if (!checking) closeConnections();
			}
			return Mono.fromFuture(stopped);
		}
		
		private void closeConnections() {
			connections.values().forEach(conn -> conn.flatMap(WebSocketClientAutoReconnection::stop).subscribe());
			connections.clear();
			stopped.complete(null);
		}
		
		private Mono<WebSocketClientAutoReconnection> connect(URI uri) {
			return webSocketClientService.autoReconnect(processor.getServiceName(), "job-control", "/api/jobcontrol/v1/websocket", reconnectEvery, pingInterval, mono -> authService.executeMonoAs(processor.getServiceName(), () -> mono))
			.doOnError(error -> {
				synchronized (connections) {
					connections.remove(uri);
				}
			})
			.doOnSuccess(client -> {
				client.addConnectionListener(conn -> {
					if (conn == null) {
						// the end
						synchronized (connections) {
							connections.remove(uri);
						}
						return;
					}
					conn.getSession().getAttributes().put(KEY_PROCESSOR, processor);
					// register message handlers
					conn.on(StartTaskRequest.class, JobProcessorClient.this::onStartTaskRequest);
					// send first message to declare the processor
					log.info("Connected to job-control WebSocketServer at {}, registering job processor", uri);
					conn.send(new JobProcessorDeclaration(processor.getServiceName(), processor.getNodeId(), processor.getParallelCapacity()))
					.subscribe();
				});
			});
		}
		
		private Mono<Void> checkInstances() {
			return Mono.defer(() -> {
				synchronized (JobProcessorConnection.this) {
					if (stopRequested) return Mono.empty();
					nextCheckSchedule = null;
					checking = true;
				}
				return discoveryClient.getInstances(jobControlServiceName)
				.flatMap(serviceInstance -> {
					URI uri = serviceInstance.getUri();
					Mono<WebSocketClientAutoReconnection> connection;
					synchronized (connections) {
						if (connections.containsKey(uri)) return Mono.empty();
						connection = connect(uri).share();
						connections.put(uri, connection);
					}
					return connection;
				})
				.onErrorComplete()
				.then(Mono.fromRunnable(() -> {
					synchronized (JobProcessorConnection.this) {
						if (stopRequested) {
							closeConnections();
							return;
						}
						nextCheckSchedule = Schedulers.parallel().schedule(() -> checkInstances().subscribe(), checkJobControlInstancesInterval.toMillis(), TimeUnit.MILLISECONDS);
						checking = false;
					}
				})); 
			});
		}
	}
	
	private JobProcessor getProcessor(WebSocketClientConnection conn) {
		return (JobProcessor) conn.getSession().getAttributes().get(KEY_PROCESSOR);
	}
	
	private Mono<Void> onStartTaskRequest(WebSocketClientConnection conn, StartTaskRequest request) {
		return getProcessor(conn).startTask(request)
			.onErrorResume(error -> Mono.just(new StartTaskResponse(request.getTaskId(), false, error.getMessage())))
			.flatMap(response -> conn.send(response));
	}
	
}
