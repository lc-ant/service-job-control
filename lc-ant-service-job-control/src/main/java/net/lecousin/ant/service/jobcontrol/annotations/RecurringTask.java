package net.lecousin.ant.service.jobcontrol.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD })
public @interface RecurringTask {

	String serviceName();
	
	String code() default "";
	
	long initialDelayMillis();
	
	long intervalMillis();
	
	int maxOccurencesToKeep() default 100;
	long maxHoursToKeepOccurences() default 24L * 30;
	
	String diplayNameNamespace();
	String displayNameKey();
	
}
