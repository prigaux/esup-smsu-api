/**
 * ESUP-Portail Example Application - Copyright (c) 2006 ESUP-Portail consortium
 * http://sourcesup.cru.fr/projects/esup-example
 */
package org.esupportail.smsuapi.services.remote; 

import java.util.LinkedList;
import java.util.List;

import org.esupportail.commons.services.application.ApplicationService;
import org.esupportail.commons.services.logging.Logger;
import org.esupportail.commons.services.logging.LoggerImpl;
import org.esupportail.smsuapi.dao.beans.Sms;
import org.esupportail.smsuapi.domain.DomainService;
import org.esupportail.smsuapi.exceptions.UnknownIdentifierApplicationException;
import org.esupportail.ws.remote.beans.MsgIdAndPhone;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * The basic implementation of the information remote service.
 */
public class SmsuapiStatus {

	@Autowired private ApplicationService applicationService;
	@Autowired private DomainService domainService;
	
	/**
	 * A logger.
	 */
	private final Logger logger = new LoggerImpl(this.getClass());
	
	
	/**
	 * Bean constructor.
	 */
	public SmsuapiStatus() {
		super();
	}
	
	/**
	 * Test if a phone number is already in the black list.
	 * @param phoneNumber
	 * @return return true if the phone number is in the bl, false otherwise
	 * @throws UnknownIdentifierApplicationException 
	 */
	public List<String> getStatus(List<MsgIdAndPhone> listMsgIdAndPhone) throws UnknownIdentifierApplicationException {
		{
			final StringBuilder sb = new StringBuilder(500);
			logger.info("Receive request for SmsuapiStatus.getStatus:");
			for (MsgIdAndPhone m : listMsgIdAndPhone) sb.append(" " + m);
			logger.info(sb.toString());
		}
		 
		List<String> l = new LinkedList<String>();

		for (MsgIdAndPhone m : listMsgIdAndPhone) {
			Sms sms = domainService.getSms(m.getMsgId(), m.getPhoneNumber());
			l.add(sms == null ? null : sms.getStateAsEnum().toString());
		}
		return l;
	}

	
	
	/**
	 * @param applicationService the applicationService to set
	 */
	public void setApplicationService(final ApplicationService applicationService) {
		this.applicationService = applicationService;
	}

	/**
	 * @param domainService the domainService to set
	 */
	public void setDomainService(final DomainService domainService) {
		this.domainService = domainService;
	}

}
