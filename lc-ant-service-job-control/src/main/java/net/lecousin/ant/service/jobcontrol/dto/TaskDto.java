package net.lecousin.ant.service.jobcontrol.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.lecousin.ant.core.validation.ValidationContext;
import net.lecousin.ant.core.validation.annotations.Ignore;
import net.lecousin.ant.core.validation.annotations.Mandatory;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskDto {

	@Ignore(context = ValidationContext.CREATION)
	private String id;
	@Mandatory(context = ValidationContext.CREATION)
	@Ignore(context = ValidationContext.UPDATE)
	private String jobId;
	@Mandatory(context = ValidationContext.CREATION)
	@Ignore(context = ValidationContext.UPDATE)
	private String serviceName;
	@Mandatory(context = ValidationContext.CREATION)
	@Ignore(context = ValidationContext.UPDATE)
	private String code;
	@Mandatory(context = ValidationContext.CREATION)
	@Ignore(context = ValidationContext.UPDATE)
	private Map<String, Serializable> input;
	@Mandatory(context = ValidationContext.CREATION)
	private long schedule;
	@Ignore
	private TaskStatus status;
	@Ignore
	private TaskResult result;
	@Mandatory(context = ValidationContext.CREATION)
	private String displayNameNamespace;
	@Mandatory(context = ValidationContext.CREATION)
	private String displayNameKey;
	
}
