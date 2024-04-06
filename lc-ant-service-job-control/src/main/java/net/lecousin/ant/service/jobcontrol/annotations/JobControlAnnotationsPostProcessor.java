package net.lecousin.ant.service.jobcontrol.annotations;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import lombok.extern.slf4j.Slf4j;
import net.lecousin.ant.core.mapping.Mappers;
import net.lecousin.ant.service.jobcontrol.dto.TaskResult;
import net.lecousin.ant.service.jobcontrol.jobs.Job;
import net.lecousin.ant.service.jobcontrol.jobs.Task;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Slf4j
public class JobControlAnnotationsPostProcessor implements BeanPostProcessor, JobControlDeclarationBean {

	private List<Task> tasks = new LinkedList<>();
	private List<Job> jobs = new LinkedList<>();
	private List<Tuple3<String, String, Map<String, Serializable>>> jobInstancesToCreate = new LinkedList<>();
	
	@Override
	public List<Task> getTasks() {
		return tasks;
	}
	
	@Override
	public List<Job> getJobs() {
		return jobs;
	}
	
	@Override
	public List<Tuple3<String, String, Map<String, Serializable>>> getJobInstancesToCreate() {
		return jobInstancesToCreate;
	}
	
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
		handleRecurringTask(targetClass, bean);
		return bean;
	}
	
	private void handleRecurringTask(Class<?> clazz, Object bean) {
		Map<Method, RecurringTask> annotatedMethods = MethodIntrospector.selectMethods(clazz, (MethodIntrospector.MetadataLookup<RecurringTask>) method -> AnnotatedElementUtils.getMergedAnnotation(method, RecurringTask.class));
		annotatedMethods.forEach((method, annotation) -> {
			log.info("Registering @RecurringTask method {}#{}", clazz.getName(), method.getName());
			String code = annotation.code().isBlank() ? clazz.getName() + '#' + method.getName() : annotation.code();
			tasks.add(new Task() {

				@Override
				public String getServiceName() {
					return annotation.serviceName();
				}

				@Override
				public String getCode() {
					return code;
				}

				@SuppressWarnings({ "unchecked", "rawtypes" })
				@Override
				public Mono<TaskResult> run(Map<String, Serializable> taskInput) {
					Mono<?> execution;
					if (Mono.class.isAssignableFrom(method.getReturnType()))
						execution = Mono.defer(() -> {
							try {
								return (Mono) method.invoke(bean);
							} catch (Throwable error) {
								return Mono.error(error);
							}
						});
					else if (Flux.class.isAssignableFrom(method.getReturnType()))
						execution = Mono.defer(() -> {
							try {
								return ((Flux) method.invoke(bean)).collectList();
							} catch (Exception e) {
								return Mono.error(e);
							}
						});
					else
						execution = Mono.fromCallable(() -> method.invoke(bean));
					return execution
					.map(Optional::of)
					.switchIfEmpty(Mono.just(Optional.empty()))
					.map(result -> {
						if (result.isEmpty()) return Map.of();
						return Mappers.OBJECT_MAPPER.convertValue(result.get(), Map.class);
					})
					.map(output -> TaskResult.success(output))
					.onErrorResume(error -> Mono.just(TaskResult.failed(error)));

				}
				
			});
			jobs.add(new net.lecousin.ant.service.jobcontrol.jobs.RecurringTask() {

				@Override
				public String getServiceName() {
					return annotation.serviceName();
				}

				@Override
				public String getCode() {
					return code;
				}

				@Override
				public Duration getInitialTaskDelay() {
					return Duration.ofMillis(annotation.initialDelayMillis());
				}

				@Override
				public Duration getTaskInterval() {
					return Duration.ofMillis(annotation.intervalMillis());
				}

				@Override
				public int getMaxOccurencesToKeep() {
					return annotation.maxOccurencesToKeep();
				}

				@Override
				public Duration getMaxDurationToKeepOccurences() {
					return annotation.maxHoursToKeepOccurences() > 0 ? Duration.ofHours(annotation.maxHoursToKeepOccurences()) : null;
				}

				@Override
				public String getDisplayNameNamespace() {
					return annotation.diplayNameNamespace();
				}

				@Override
				public String getDisplayNameKey() {
					return annotation.displayNameKey();
				}

			});
			jobInstancesToCreate.add(Tuples.of(annotation.serviceName(), code, Map.of()));
		});
	}
	
}
