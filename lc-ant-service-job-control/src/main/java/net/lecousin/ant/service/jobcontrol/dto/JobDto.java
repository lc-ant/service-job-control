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
public class JobDto implements Serializable {

	private static final long serialVersionUID = 1L;
	
	@Ignore(context = ValidationContext.CREATION)
	private String id;
	@Ignore(context = ValidationContext.CREATION)
	private String parentId;
	@Mandatory(context = ValidationContext.CREATION)
	@Ignore(context = ValidationContext.UPDATE)
	private String serviceName;
	@Mandatory(context = ValidationContext.CREATION)
	@Ignore(context = ValidationContext.UPDATE)
	private String code;
	private Map<String, Serializable> data;
	private String displayNameNamespace;
	private String displayNameKey;
	
}
