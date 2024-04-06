package net.lecousin.ant.service.jobcontrol;

import reactor.core.publisher.Mono;

public interface JobProcessorService {

	Mono<Void> registerProcessor(JobProcessor processor);
	
	Mono<Void> unregisterProcessor(JobProcessor processor);

}
