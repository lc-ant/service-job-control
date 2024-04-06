package net.lecousin.ant.service.jobcontrol.dto;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskResult {

	private Status status;
	private String message;
	private Map<String, Serializable> output;
	
	public enum Status {
		SUCCESS, FAILED, CANCELED;
	}
	
	public static TaskResult success(Map<String, Serializable> output) {
		return new TaskResult(Status.SUCCESS, null, output);
	}
	
	public static TaskResult success() {
		return success(null);
	}
	
	public static TaskResult failed(String errorMessage, Map<String, Serializable> output) {
		return new TaskResult(Status.FAILED, errorMessage, output);
	}
	
	public static TaskResult failed(String errorMessage) {
		return failed(errorMessage, null);
	}
	
	public static TaskResult failed(Throwable error) {
		return failed(error.getMessage(), null);
	}
	
	public static TaskResult failed(Throwable error, Map<String, Serializable> output) {
		return failed(error.getMessage(), output);
	}
	
	public static TaskResult canceled(String message, Map<String, Serializable> output) {
		return new TaskResult(Status.CANCELED, message, output);
	}
	
	public static TaskResult canceled(String message) {
		return canceled(message, null);
	}
	
}
