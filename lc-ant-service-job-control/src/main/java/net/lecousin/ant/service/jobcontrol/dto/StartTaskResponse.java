package net.lecousin.ant.service.jobcontrol.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StartTaskResponse implements Serializable {

	private static final long serialVersionUID = 1L;
	
	private String taskId;
	private boolean accepted;
	private String fatalErrorMessage;
	
}
