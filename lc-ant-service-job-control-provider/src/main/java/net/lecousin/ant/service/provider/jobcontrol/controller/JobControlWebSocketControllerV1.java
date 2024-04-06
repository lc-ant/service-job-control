package net.lecousin.ant.service.provider.jobcontrol.controller;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import lombok.RequiredArgsConstructor;
import net.lecousin.ant.core.api.exceptions.UnauthorizedException;
import net.lecousin.ant.core.security.LcAntSecurity;
import net.lecousin.ant.core.springboot.security.SecurityUtils;
import net.lecousin.ant.core.springboot.service.provider.websocket.WebSocketController;
import net.lecousin.ant.core.springboot.service.provider.websocket.WebSocketServerHandler.WebSocketServerSession;
import net.lecousin.ant.service.jobcontrol.JobProcessor;
import net.lecousin.ant.service.jobcontrol.dto.JobProcessorDeclaration;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskRequest;
import net.lecousin.ant.service.jobcontrol.dto.StartTaskResponse;
import net.lecousin.ant.service.provider.jobcontrol.JobControlServiceProvider;
import net.lecousin.ant.service.provider.jobcontrol.JobProcessorServiceProvider;
import net.lecousin.ant.service.provider.jobcontrol.exceptions.WrongServiceAuthenticationException;
import reactor.core.publisher.Mono;

@Component
@WebSocketController(path = "/api/jobcontrol/v1/websocket", serviceName = JobControlServiceProvider.SERVICE_NAME)
@RequiredArgsConstructor
public class JobControlWebSocketControllerV1 {
	
	private static final String KEY_PROCESSOR = "job-control-client-processor";

	private final JobProcessorServiceProvider jobProcessorService;
	
	@WebSocketController.OnSessionClosed
	public Mono<Void> onSessionClosed(WebSocketServerSession session) {
		return jobProcessorService.unregisterProcessor(getExecutor(session));
	}
	
	public Mono<Void> start(WebSocketServerSession session, @RequestBody JobProcessorDeclaration declaration) {
		if (session.getAuthentication() == null) return Mono.error(new UnauthorizedException());
		if (!SecurityUtils.isSubject(session.getAuthentication(), LcAntSecurity.SUBJECT_TYPE_SERVICE, declaration.getServiceName()))
			return Mono.error(new WrongServiceAuthenticationException(declaration.getServiceName()));
		return new ClientJobProcessor(session, declaration).register();
	}
	
	public Mono<Void> startTaskResponse(WebSocketServerSession session, @RequestBody StartTaskResponse response) {
		return getExecutor(session).startTaskResponse(response);
	}
	
	private ClientJobProcessor getExecutor(WebSocketServerSession session) {
		return (ClientJobProcessor) session.getAttributes().get(KEY_PROCESSOR);
	}
	
	private final class ClientJobProcessor implements JobProcessor {
		
		private final WebSocketServerSession session;
		private final JobProcessorDeclaration declaration;
		
		private final Map<String, CompletableFuture<StartTaskResponse>> startTask = new HashMap<>();
		
		public ClientJobProcessor(WebSocketServerSession session, JobProcessorDeclaration declaration) {
			this.session = session;
			this.declaration = declaration;
			session.getAttributes().put(KEY_PROCESSOR, this);
		}
		
		public Mono<Void> register() {
			return jobProcessorService.registerProcessor(this);
		}
		
		@Override
		public String getServiceName() {
			return declaration.getServiceName();
		}
		
		@Override
		public String getNodeId() {
			return declaration.getNodeId();
		}
		
		@Override
		public int getParallelCapacity() {
			return declaration.getParallelCapacity();
		}
		
		@Override
		public Collection<String> getPendingTaskIds() {
			return startTask.keySet();
		}
		
		@Override
		public Mono<StartTaskResponse> startTask(StartTaskRequest request) {
			CompletableFuture<StartTaskResponse> future = new CompletableFuture<>();
			synchronized (startTask) {
				startTask.put(request.getTaskId(), future);
			}
			return session.send(request).then(Mono.fromFuture(future));
		}
		
		private Mono<Void> startTaskResponse(StartTaskResponse response) {
			CompletableFuture<StartTaskResponse> future;
			synchronized (startTask) {
				future = startTask.remove(response.getTaskId());
			}
			if (future != null) future.complete(response);
			return Mono.empty();
		}
		
	}
}
