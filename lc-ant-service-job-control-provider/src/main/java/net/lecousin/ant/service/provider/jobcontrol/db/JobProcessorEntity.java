package net.lecousin.ant.service.provider.jobcontrol.db;

import java.util.LinkedList;

import org.springframework.data.annotation.Id;

import lombok.Data;
import net.lecousin.ant.connector.database.annotations.Entity;
import net.lecousin.ant.connector.database.annotations.GeneratedValue;
import net.lecousin.ant.connector.database.annotations.Index;
import net.lecousin.ant.connector.database.model.IndexType;
import net.lecousin.ant.core.api.ApiData;
import net.lecousin.ant.core.expression.impl.CollectionFieldReference;
import net.lecousin.ant.core.expression.impl.NumberFieldReference;
import net.lecousin.ant.core.expression.impl.StringFieldReference;

@Entity(domain = "jobcontrol", name = "processor")
@Data
@Index(fields = { "serviceName", "nodeId" }, type = IndexType.UNIQUE)
public class JobProcessorEntity {

	public static final StringFieldReference FIELD_ID = ApiData.FIELD_ID;
	public static final StringFieldReference FIELD_SERVICE_NAME = new StringFieldReference("serviceName");
	public static final StringFieldReference FIELD_NODE_ID = new StringFieldReference("nodeId");
	public static final NumberFieldReference<Long> FIELD_LAST_SEEN = new NumberFieldReference<>("lastSeen");
	public static final NumberFieldReference<Integer> FIELD_PARALLEL_CAPACITY = new NumberFieldReference<>("parallelCapacity");
	public static final CollectionFieldReference<String, LinkedList<String>> FIELD_PROCESSING_TASKS = new CollectionFieldReference<>("processingTasks");
	
	@Id
	@GeneratedValue
	private String id;
	
	private String serviceName;
	private String nodeId;
	
	private long lastSeen = 0;
	
	private int parallelCapacity;
	private LinkedList<String> processingTasks = new LinkedList<>();
	
}
