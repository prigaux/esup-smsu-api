/**
 * ESUP-Portail Example Application - Copyright (c) 2006 ESUP-Portail consortium
 * http://sourcesup.cru.fr/projects/esup-example
 */
package org.esupportail.smsuapi.services.remote; 

import java.io.Serializable;

import org.esupportail.smsuapi.exceptions.UnknownIdentifierApplicationException;
import org.esupportail.smsuapi.exceptions.UnknownIdentifierMessageException;
import org.esupportail.ws.remote.beans.TrackInfos;

/**
 * The interface of the information remote service.
 */
public interface SendTrack extends Serializable {

	/**
	 * @return list of :
	 *  - the number of SMS recipients.
	 *  - the non-authorized phone numbers (in back list).
	 *  - the number of sent SMS.
	 */
	
	TrackInfos getTrackInfos(Integer msgId) 
			throws UnknownIdentifierApplicationException, UnknownIdentifierMessageException;

}
