package org.esupportail.smsuapi.services.servlet;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;
import org.esupportail.smsuapi.exceptions.InsufficientQuotaException;
import org.esupportail.smsuapi.exceptions.InvalidParameterException;
import org.esupportail.smsuapi.exceptions.UnknownMessageIdException;
import org.esupportail.ws.remote.beans.MsgIdAndPhone;
import org.springframework.beans.factory.annotation.Autowired;

public class RestServletActions {

	@SuppressWarnings("unused")
	private final Logger logger = Logger.getLogger(getClass());

	@Autowired private org.esupportail.smsuapi.business.ClientManager clientManager;
	@Autowired private org.esupportail.smsuapi.business.SendSmsManager sendSms;
	@Autowired private org.esupportail.smsuapi.domain.DomainService domainService;
	@Autowired private org.esupportail.smsuapi.business.BlackListManager blackListManager;

    	public Object wsActionSendSms(HttpServletRequest req) throws InsufficientQuotaException {
		Integer id = sendSMS(getInteger(req, "id", null),
			getInteger(req, "senderId", null),
			getStrings(req, "phoneNumber"),
			getString(req, "account", null), 
			getString(req, "message"));
		return put(singletonMap("status", (Object)"OK"), "id", id);
	}

	/**
	   Since SendSms.sendSMS silently fail,
	   and since we do not want to modify SOAP, we behave differently in REST: we do check first.
	 */
	private Integer sendSMS(Integer msgId, Integer senderId, 
			     String[] smsPhones, String labelAccount, String msgContent) throws InsufficientQuotaException {
		sendSms.mayCreateAccountAndCheckQuotaOk(smsPhones.length, labelAccount);
		return sendSms.sendSMS(msgId, senderId, smsPhones, labelAccount, msgContent);
	}

	public Object wsActionMayCreateAccountCheckQuotaOk(HttpServletRequest req) throws InsufficientQuotaException {
		sendSms.mayCreateAccountAndCheckQuotaOk(
				getInteger(req, "nbDest"), getString(req, "account"));
		return singletonMap("status", "OK");
	}
	
	public Object wsActionIsBlacklisted(HttpServletRequest req) {    
		return blackListManager.isPhoneNumberInBlackList(getString(req, "phoneNumber"));
	}

	public Object wsActionListPhoneNumbersInBlackList(HttpServletRequest req) {
		return blackListManager.getListPhoneNumbersInBlackList();
	}

    	public Object wsActionMessageInfos(HttpServletRequest req) throws UnknownMessageIdException {    
		return domainService.getTrackInfos(getInteger(req, "id"));
	}

    	public Object wsActionMessageStatus(HttpServletRequest req) throws UnknownMessageIdException {
		String[] ids = getStrings(req, "id");
		String[] phoneNumbers = getStrings(req, "phoneNumber");
		if (ids.length != phoneNumbers.length) {
			throw new InvalidParameterException("there must be same number of parameters \"id\" and \"phoneNumber\"");
		}
		List<MsgIdAndPhone> list = new ArrayList<>();
		for (int i = 0; i < ids.length; i++) {
			list.add(new MsgIdAndPhone(Integer.parseInt(ids[i]), phoneNumbers[i]));
		}
		return domainService.getStatus(list);
	}

	boolean isAuthValid() {
		return clientManager.getApplicationOrNull() != null;
	}


	static String[] getStrings(HttpServletRequest req, String name) {
		String[] vals = req.getParameterValues(name);
		if (vals == null) throw new InvalidParameterException("\"" + name + "\" parameter is mandatory");
		return vals;
	}
	static String getString(HttpServletRequest req, String name) {
		String val = getString(req, name, null);
		if (val == null) throw new InvalidParameterException("\"" + name + "\" parameter is mandatory");
		return val;
	}
	static String getString(HttpServletRequest req, String name, String defaultValue) {
		String val = req.getParameter(name);
		return val != null ? val : defaultValue;
	}

	static Integer getInteger(HttpServletRequest req, String name) {
		try {
			return Integer.parseInt(getString(req, name));
		} catch (NumberFormatException e) {
			throw new InvalidParameterException("\"" + name + "\" parameter must be an integer");
		}
	}
	static Integer getInteger(HttpServletRequest req, String name, Integer defaultValue) {
		String s = getString(req, name, null);
		try {
			return s != null ? Integer.valueOf(s) : defaultValue;
 		} catch (NumberFormatException e) {
			throw new InvalidParameterException("\"" + name + "\" parameter must be an integer");
		}

	}

	private <A,B> Map<A,B> singletonMap(A k, B v) {
		return put(new TreeMap<A,B>(), k, v);
	}	

	private <A,B> Map<A,B> put(Map<A,B> m, A k, B v) {
		m.put(k,v);
		return m;
	}	

	
	
	/**
	 * Standard setter used by spring.
	 */
	public void setClientManager(final org.esupportail.smsuapi.business.ClientManager clientManager) {
		this.clientManager = clientManager;
	}

	/**
	 * Standard setter used by spring.
	 */
	public void setSendSms(final org.esupportail.smsuapi.business.SendSmsManager sendSms) {
		this.sendSms = sendSms;
	}

	/**
	 * Standard setter used by spring.
	 */
	public void setDomainService(final org.esupportail.smsuapi.domain.DomainService domainService) {
		this.domainService = domainService;
	}

}
