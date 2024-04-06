package net.lecousin.ant.service.provider.jobcontrol.exceptions;

import net.lecousin.ant.core.api.exceptions.ForbiddenException;
import net.lecousin.commons.io.text.i18n.TranslatedString;

public class WrongServiceAuthenticationException extends ForbiddenException {

	private static final long serialVersionUID = 1L;

	public WrongServiceAuthenticationException(String expectedServiceName) {
		super(new TranslatedString("service-job-control", "wrong service authentication", expectedServiceName));
	}
	
}
