package net.lecousin.ant.service.provider.jobcontrol.db;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.lecousin.ant.connector.database.annotations.Entity;
import net.lecousin.ant.connector.database.annotations.GeneratedValue;
import net.lecousin.ant.connector.database.annotations.Index;
import net.lecousin.ant.connector.database.model.IndexType;
import net.lecousin.ant.core.api.ApiData;
import net.lecousin.ant.core.expression.impl.StringFieldReference;

@Entity(domain = "jobcontrol", name = "job")
@Data
@Index(fields = { "serviceName", "code" }, type = IndexType.UNIQUE)
@NoArgsConstructor
@AllArgsConstructor
public class JobEntity {
	
	public static final StringFieldReference FIELD_ID = ApiData.FIELD_ID;
	public static final StringFieldReference.Nullable FIELD_PARENT_ID = new StringFieldReference.Nullable("parentId");
	public static final StringFieldReference FIELD_SERVICE_NAME = new StringFieldReference("serviceName");
	public static final StringFieldReference FIELD_CODE = new StringFieldReference("code");
	public static final StringFieldReference FIELD_DISPLAY_NAME_NAMESPACE = new StringFieldReference("displayNameNamespace");
	public static final StringFieldReference FIELD_DISPLAY_NAME_KEY = new StringFieldReference("displayNameKey");

	@Id
	@GeneratedValue
	private String id;
	
	private Optional<String> parentId;
	
	private String serviceName;
	private String code;
	
	private Map<String, Serializable> data;
	
	private String displayNameNamespace;
	private String displayNameKey;
	
}
