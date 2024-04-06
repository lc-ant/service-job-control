package net.lecousin.ant.service.jobcontrol.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StartTaskRequest implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String taskCode;
	private String taskId;
	private Map<String, Serializable> taskInput;
	private String jobId;
	private String jobCode;
	private String correlationId;
	
}
