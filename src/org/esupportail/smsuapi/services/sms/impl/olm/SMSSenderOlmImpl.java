package org.esupportail.smsuapi.services.sms.impl.olm;

import org.esupportail.commons.services.logging.Logger;
import org.esupportail.commons.services.logging.LoggerImpl;
import org.esupportail.smsuapi.domain.beans.sms.SMSBroker;
import org.esupportail.smsuapi.services.sms.ISMSSender;

import fr.cvf.util.mgs.message.NotificationLevel;
import fr.cvf.util.mgs.mode.sgs.impl.message.request.RequestFactory;
import fr.cvf.util.mgs.mode.sgs.message.request.SMText;

/**
 * Olm implementation of the SMS sender.
 * @author PRQD8824
 *
 */
public class SMSSenderOlmImpl implements ISMSSender {

	/**
	 * Log4j logger.
	 */
	private final Logger logger = new LoggerImpl(getClass());
	
	/**
	 * SMS notification level.
	 */
	private static final int NOTIFICATION_LEVEL = NotificationLevel.FINAL;
	
	/**
	 * Olm connector used to send message.
	 */
	private OlmConnector olmConnector;
	
	/**
	 * use to simulate sending.
	 */
	private boolean simulateMessageSending;
	

	/* (non-Javadoc)
	 * @see org.esupportail.smsuapi.services.sms.ISMSSender
	 * #sendMessage(org.esupportail.smsuapi.domain.beans.sms.SMSMessage)
	 */
	public void sendMessage(final SMSBroker sms) {
		
		final int smsId = sms.getId();
		final String smsRecipient = sms.getRecipient();
		final String smsMessge = sms.getMessage();
		
		if (logger.isDebugEnabled()) {
			logger.debug("Entering into send message with parameter : ");
			logger.debug("   - message id : " + smsId);
			logger.debug("   - message recipient : " + smsRecipient);
			logger.debug("   - message : " + smsMessge);
		}
		
		try {
			final SMText smText = RequestFactory.createSMText(smsId, smsRecipient, smsMessge);
			smText.setNotificationLevel(NOTIFICATION_LEVEL);
			
			// only send the message if required
			if (!simulateMessageSending) {
				olmConnector.submit(smText);
				if (logger.isDebugEnabled()) {
					final StringBuilder sb = new StringBuilder();
					sb.append("message with : ");
					sb.append(" - id : ").append(smsId);
					sb.append("successfully sent");
					logger.debug(sb.toString());
				}
			} else {
				final StringBuilder sb = new StringBuilder();
				sb.append("Message with id : ").append(smsId);
				sb.append(" not sent because simlation mode is enable");
				logger.warn(sb.toString());
			}

			
		} catch (Throwable t) {
			final StringBuilder message = new StringBuilder();
			message.append("An error occurs sending SMS : ");
			message.append(" - id : ").append(smsId);
			message.append(" - recipient : ").append(smsRecipient);
			message.append(" - message : ").append(smsMessge);
			logger.error(message.toString());
		}		

	}
	
	
	/*******************
	 * Mutator.
	 *******************/

	/**
	 * Standard setter used by Spring.
	 */
	public void setOlmConnector(final OlmConnector olmConnector) {
		this.olmConnector = olmConnector;
	}
	
	/**
	 * Standard setter used by spring.
	 * @param simulateMessageSending
	 */
	public void setSimulateMessageSending(final boolean simulateMessageSending) {
		this.simulateMessageSending = simulateMessageSending;
	}
}
