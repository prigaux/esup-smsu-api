/**
 * ESUP-Portail Example Application - Copyright (c) 2006 ESUP-Portail consortium
 * http://sourcesup.cru.fr/projects/esup-example
 */
package org.esupportail.smsuapi.services.remote; 

import java.util.Iterator;
import java.util.Set;

import org.esupportail.commons.services.application.ApplicationService;
import org.esupportail.commons.services.logging.Logger;
import org.esupportail.commons.services.logging.LoggerImpl;
import org.esupportail.commons.services.remote.AbstractIpProtectedWebService;
import org.esupportail.commons.utils.Assert;
import org.esupportail.smsuapi.domain.DomainService;
import org.esupportail.smsuapi.exceptions.UnknownIdentifierApplicationException;
import org.esupportail.smsuapi.exceptions.UnknownIdentifierMessageException;
import org.esupportail.ws.remote.beans.TrackInfos;

/**
 * The basic implementation of the information remote service.
 */
public class SendTrackImpl extends AbstractIpProtectedWebService implements SendTrack {

	/**
	 * The serialization id.
	 */
	private static final long serialVersionUID = 4480257087458550019L;

	/**
	 * A logger.
	 */
	private final Logger logger = new LoggerImpl(getClass());

	/**
	 * The application service.
	 */
	private ApplicationService applicationService;
	
	/**
	 * The domain service.
	 */
	private DomainService domainService;
	
	/**
	 * Bean constructor.
	 */
	public SendTrackImpl() {
		super();
	}

	/**
	 * @see org.esupportail.commons.services.remote.AbstractIpProtectedWebService#afterPropertiesSet()
	 */
	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		Assert.notNull(applicationService, 
				"property applicationService of class " + this.getClass().getName() 
				+ " can not be null");
		Assert.notNull(domainService, 
				"property domainService of class " + this.getClass().getName() 
				+ " can not be null");
	}
	
	public TrackInfos getTrackInfos(final Integer msgId) 
			throws UnknownIdentifierApplicationException, UnknownIdentifierMessageException {
		if (logger.isDebugEnabled()) {
		logger.debug("WS SendTrack receives the client request with parameter msgId = " + msgId);
		}
		TrackInfos infos = new TrackInfos();
		infos.setNbDestTotal(domainService.getNbDest(msgId));
		infos.setNbDestBlackList(domainService.getNbDestBackList(msgId));
		infos.setNbSentSMS(domainService.getNbSentSMS(msgId));
		infos.setNbProgressSMS(domainService.getNbProgressSMS(msgId));
		infos.setNbErrorSMS(domainService.getNbErrorSMS(msgId));
		infos.setListNumErreur(domainService.getListNumErreur(msgId));
		
		if (logger.isDebugEnabled()) {
			final StringBuilder sb = new StringBuilder(300);
			sb.append("Response TrackInfos object, for the client of WS SendTrack : ");
			sb.append("TrackInfos.NbDestTotal : ").append(infos.getNbDestTotal().toString());
			sb.append("TrackInfos.NbSentSMS : ").append(infos.getNbSentSMS().toString());
			sb.append("TrackInfos.NbProgressSMS : ").append(infos.getNbProgressSMS().toString());
			sb.append("TrackInfos.NbDestBlackList :").append(infos.getNbDestBlackList().toString());
			sb.append("TrackInfos.NbErrorSMS : ").append(infos.getNbErrorSMS().toString());
			logger.debug(sb.toString());
		
		Set<String> listnums = infos.getListNumErreur();
		Iterator<String> iter = listnums.iterator();
	    while (iter.hasNext()) {
	    	String phone = (String) iter.next();
	    	logger.debug("TrackInfos.NumErreur : " + phone);
	    	}
	    
	    }
		return infos;
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
