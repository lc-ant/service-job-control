package net.lecousin.ant.service.jobcontrol.annotations;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import net.lecousin.ant.service.jobcontrol.jobs.Job;
import net.lecousin.ant.service.jobcontrol.jobs.Task;
import reactor.util.function.Tuple3;

public interface JobControlDeclarationBean {

	List<Task> getTasks();
	
	List<Job> getJobs();
	
	List<Tuple3<String, String, Map<String, Serializable>>> getJobInstancesToCreate();
	
}
