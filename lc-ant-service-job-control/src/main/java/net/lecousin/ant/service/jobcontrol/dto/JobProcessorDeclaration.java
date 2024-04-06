package net.lecousin.ant.service.jobcontrol.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobProcessorDeclaration implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String serviceName;
	private String nodeId;
	private int parallelCapacity;

}
