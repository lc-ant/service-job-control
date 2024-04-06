package net.lecousin.ant.service.provider.jobcontrol.db;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.lecousin.ant.connector.database.annotations.Entity;
import net.lecousin.ant.connector.database.annotations.GeneratedValue;
import net.lecousin.ant.core.api.ApiData;
import net.lecousin.ant.core.expression.impl.EnumFieldReference;
import net.lecousin.ant.core.expression.impl.FieldReference;
import net.lecousin.ant.core.expression.impl.NumberFieldReference;
import net.lecousin.ant.core.expression.impl.StringFieldReference;
import net.lecousin.ant.service.jobcontrol.dto.TaskStatus;

@Entity(domain = "jobcontrol", name = "task")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskEntity {
	
	public static final StringFieldReference FIELD_ID = ApiData.FIELD_ID;
	public static final NumberFieldReference<Long> FIELD_VERSION = ApiData.FIELD_VERSION;
	public static final StringFieldReference FIELD_JOB_ID = new StringFieldReference("jobId");
	public static final StringFieldReference FIELD_JOB_CODE = new StringFieldReference("jobCode");
	public static final StringFieldReference FIELD_SERVICE_NAME = new StringFieldReference("serviceName");
	public static final StringFieldReference FIELD_CODE = new StringFieldReference("code");
	public static final StringFieldReference FIELD_DISPLAY_NAME_NAMESPACE = new StringFieldReference("displayNameNamespace");
	public static final StringFieldReference FIELD_DISPLAY_NAME_KEY = new StringFieldReference("displayNameKey");
	public static final NumberFieldReference<Long> FIELD_SCHEDULE = new NumberFieldReference<>("schedule");
	public static final NumberFieldReference<Long> FIELD_CREATED_AT = new NumberFieldReference<>("createdAt");
	public static final NumberFieldReference.Nullable<Long> FIELD_PROCESSING_START = new NumberFieldReference.Nullable<>("processingStart");
	public static final NumberFieldReference.Nullable<Long> FIELD_PROCESSING_END = new NumberFieldReference.Nullable<>("processingEnd");
	public static final StringFieldReference.Nullable FIELD_PROCESSING_NODE_ID = new StringFieldReference.Nullable("processingNodeId");
	public static final StringFieldReference.Nullable FIELD_CORRELATION_ID = new StringFieldReference.Nullable("correlationId");
	public static final EnumFieldReference<TaskStatus> FIELD_STATUS = new EnumFieldReference<>("status");
	public static final StringFieldReference.Nullable FIELD_STATUS_DETAILS = new StringFieldReference.Nullable("statusDetails");
	public static final NumberFieldReference<Long> FIELD_LAST_RESERVATION_TIME = new NumberFieldReference<>("lastReservationTime");
	public static final NumberFieldReference.Nullable<Long> FIELD_RESERVATION_TIME = new NumberFieldReference.Nullable<>("reservationTime");
	public static final FieldReference<Serializable> FIELD_INPUT = new FieldReference<>("input");
	public static final FieldReference<Serializable> FIELD_OUTPUT = new FieldReference<>("input");
	

	@Id
	@GeneratedValue
	private String id;
	@Version
	private long version;
	
	private String jobId;
	private String jobCode;
	
	private String serviceName;
	private String code;
	
	private String displayNameNamespace;
	private String displayNameKey;
	
	private long schedule;

	private long createdAt;
	private Long processingStart;
	private Long processingEnd;
	private Optional<String> processingNodeId;
	private Optional<String> correlationId;

	private TaskStatus status;
	private String statusDetails;
	
	private Map<String, Serializable> input;
	private Map<String, Serializable> output;
	
	private long lastReservationTime = 0;
	private Long reservationTime = null;
	
}
