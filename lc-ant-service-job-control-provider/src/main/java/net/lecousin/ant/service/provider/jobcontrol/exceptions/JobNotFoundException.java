package net.lecousin.ant.service.provider.jobcontrol.exceptions;

import net.lecousin.ant.core.api.exceptions.NotFoundException;
import net.lecousin.commons.io.text.i18n.TranslatedString;

public class JobNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 1L;

	public JobNotFoundException(String jobId) {
		super(new TranslatedString("service-job-control", "job {} not found", jobId), "job");
	}
	
}
