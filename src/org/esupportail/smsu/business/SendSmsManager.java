package org.esupportail.smsu.business;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.esupportail.commons.services.database.DatabaseUtils;
import org.esupportail.commons.services.i18n.I18nService;
import org.esupportail.commons.services.ldap.LdapUser;
import org.esupportail.commons.services.logging.Logger;
import org.esupportail.commons.services.logging.LoggerImpl;
import org.esupportail.portal.ws.client.PortalGroup;
import org.esupportail.portal.ws.client.PortalGroupHierarchy;
import org.esupportail.smsu.business.beans.CustomizedMessage;
import org.esupportail.smsu.dao.DaoService;
import org.esupportail.smsu.dao.beans.Account;
import org.esupportail.smsu.dao.beans.BasicGroup;
import org.esupportail.smsu.dao.beans.CustomizedGroup;
import org.esupportail.smsu.dao.beans.Mail;
import org.esupportail.smsu.dao.beans.MailRecipient;
import org.esupportail.smsu.dao.beans.Message;
import org.esupportail.smsu.dao.beans.Person;
import org.esupportail.smsu.dao.beans.Recipient;
import org.esupportail.smsu.dao.beans.Service;
import org.esupportail.smsu.dao.beans.Template;
import org.esupportail.smsu.domain.beans.mail.MailStatus;
import org.esupportail.smsu.domain.beans.message.MessageStatus;
import org.esupportail.smsu.exceptions.BackOfficeUnrichableException;
import org.esupportail.smsu.exceptions.InsufficientQuotaException;
import org.esupportail.smsu.exceptions.UnknownIdentifierApplicationException;
import org.esupportail.smsu.exceptions.ldap.LdapUserNotFoundException;
import org.esupportail.smsu.groups.pags.SmsuPersonAttributesGroupStore;
import org.esupportail.smsu.groups.pags.SmsuPersonAttributesGroupStore.GroupDefinition;
import org.esupportail.smsu.services.client.SendSmsClient;
import org.esupportail.smsu.services.ldap.LdapUtils;
import org.esupportail.smsu.services.scheduler.SchedulerUtils;
import org.esupportail.smsu.services.smtp.SmtpServiceUtils;
import org.esupportail.smsu.web.beans.GroupRecipient;
import org.esupportail.smsu.web.beans.MailToSend;
import org.esupportail.smsu.web.beans.UiRecipient;



/**
 * @author xphp8691
 *
 */
public class SendSmsManager  {


	/**
	 * {@link DaoService}.
	 */
	private DaoService daoService;

	/**
	 * {@link I18nService}.
	 */
	private I18nService i18nService;

	/**
	 * link to access to the sms client layer.
	 */
	private SendSmsClient sendSmsClient;

	/**
	 * the max SMS number before the message has to be validated.
	 */
	private Integer nbMaxSmsBeforeSupervising;

	/**
	 * the default Supervisor login when the max SMS number is reach.
	 */
	private String defaultSupervisorLogin;

	/**
	 * {@link SmtpServiceUtils}.
	 */
	private SmtpServiceUtils smtpServiceUtils;

	/**
	 *  {@link LdapUtils}.
	 */
	private LdapUtils ldapUtils;

	/**
	 * Used to launch task.
	 */
	private SchedulerUtils schedulerUtils;

	/**
	 * The SMS max size.
	 */
	private Integer smsMaxSize;

	/**
	 * The default account.
	 */
	private String defaultAccount;

	/**
	 * used to customize the content.
	 */
	private ContentCustomizationManager customizer;

	/**
	 * the LDAP Email attribute.
	 */
	private String userEmailAttribute;

	/**
	 * The pager attributeName.
	 */
	private String userPagerAttribute;

	/**
	 * The key used to represent the CG in the ldap (up1terms).
	 */
	private String cgKeyName;

	private SmsuPersonAttributesGroupStore smsuPersonAttributesGroupStore;

	/**
	 * attribut used to get user's services
	 */
	private String userTermsOfUseAttribute;
	
	/**
	 * Log4j logger.
	 */
	private final Logger logger = new LoggerImpl(getClass());

	//////////////////////////////////////////////////////////////
	// Constructors
	//////////////////////////////////////////////////////////////
	/**
	 * Bean constructor.
	 */
	public SendSmsManager() {
		super();
	}

	//////////////////////////////////////////////////////////////
	// Pricipal methods
	//////////////////////////////////////////////////////////////
	/**
	 * @param uiRecipients 
	 * @param login 
	 * @param content 
	 * @param smsTemplate 
	 * @param userGroup 
	 * @param serviceId 
	 * @param mailToSend 
	 * @return a message.
	 */
	public Message composeMessage(final List<UiRecipient> uiRecipients, 
			final String login, final String content, final String smsTemplate, final String userGroup,
			final Integer serviceId, final MailToSend mailToSend) {
		Message message = new Message();

		message.setContent(content);

		////////////////Message template///////////////////
		if (smsTemplate != null) {
			Template tpl = getMessageTemplate(smsTemplate);
			message.setTemplate(tpl);
		}

		////////////////Sender informations///////////////////
		Person sender =	getSender(login);
		message.setSender(sender);

		Account account = getAccount(userGroup);
		message.setAccount(account);

		Service service = getService(serviceId);
		message.setService(service);

		BasicGroup groupSender = getGroupSender(userGroup);
		message.setGroupSender(groupSender);

		////////////////Recipients///////////////////

		Set<Recipient> recipients;
		recipients = getRecipients(uiRecipients, service);

		message.setRecipients(recipients);

		if (logger.isDebugEnabled()) {
			logger.debug("get recipient group");
		}
		BasicGroup groupRecipient = getGroupRecipient(uiRecipients);
		message.setGroupRecipient(groupRecipient);
		if (logger.isDebugEnabled()) {
			logger.debug("get workflow state");
		}
		MessageStatus state = getWorkflowState(message);
		message.setStateAsEnum(state);

		if (logger.isDebugEnabled()) {
			logger.debug("get supervisors");
		}
		Set<Person> supervisors;
		//	message.getGroupRecipient() != null | 
		if (MessageStatus.WAITING_FOR_APPROVAL.equals(message.getStateAsEnum())) {
			if (logger.isDebugEnabled()) {
				logger.debug("Supervisors needed");
			}
			supervisors = getSupervisors(message);
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("No supervisors needed");
			}
			supervisors = null;
		}
		message.setSupervisors(supervisors);

		if (mailToSend != null) {
			Mail mail = getMail(message, mailToSend);
			message.setMail(mail);
		}

		//the message is not saved if the front office quotas check failed.
		if ((!MessageStatus.FO_QUOTA_ERROR.equals(message.getStateAsEnum()))
				&&(!MessageStatus.FO_NB_MAX_CUSTOMIZED_GROUP_ERROR.equals(message.getStateAsEnum()))) {
			daoService.addMessage(message);
		}

		return message;
	}

	/**
	 * @param message
	 * @return the treatment result
	 * @throws BackOfficeUnrichableException 
	 * @throws LdapUserNotFoundException 
	 * @throws UnknownIdentifierApplicationException 
	 * @throws InsufficientQuotaException 
	 */
	public String treatMessage(final Message message)
	throws BackOfficeUnrichableException, LdapUserNotFoundException,
	UnknownIdentifierApplicationException, InsufficientQuotaException {
		try {
			if (message.getStateAsEnum().equals(MessageStatus.NO_RECIPIENT_FOUND))
				return "NORECIPIENTFOUND";
			if (message.getStateAsEnum().equals(MessageStatus.FO_NB_MAX_CUSTOMIZED_GROUP_ERROR))
				return "FONBMAXFORCUSTOMIZEDGROUPERROR";
			if (message.getStateAsEnum().equals(MessageStatus.FO_QUOTA_ERROR))
				return "FOQUOTAKO";
			if (!message.getStateAsEnum().equals(MessageStatus.WAITING_FOR_APPROVAL)) {

							/////check the quotas with the back office/////
							Integer nbToSend = message.getRecipients().size();
							String accountLabel = message.getUserAccountLabel();

							checkBackOfficeQuotas(nbToSend, accountLabel);

								// message is ready to be sent to the back office
								if (logger.isDebugEnabled()) {
									logger.debug("Setting to state WAINTING_FOR_SENDING message with ID (standard message case) : " + message.getId());
								}
								message.setStateAsEnum(MessageStatus.WAITING_FOR_SENDING);
								daoService.updateMessage(message);

								// launch ASAP the task witch manage the sms sending
								schedulerUtils.launchSuperviseSmsSending();

						} else {
							// envoi du mail
							if (message.getSupervisors() != null) {
								if (logger.isDebugEnabled()) {
									logger.debug("supervisors not null");
								}
								sendMailsToSupervisors(message.getSupervisors());	
							}

						}	
						return "TRAITEMENTOK";
		} catch (UnknownIdentifierApplicationException e) {
			message.setStateAsEnum(MessageStatus.WS_ERROR);
			daoService.updateMessage(message);
			throw e;
		} catch (InsufficientQuotaException e) {
			message.setStateAsEnum(MessageStatus.WS_QUOTA_ERROR);
			daoService.updateMessage(message);
			throw e;	
		} catch (BackOfficeUnrichableException e) {
			message.setStateAsEnum(MessageStatus.WS_ERROR);
			daoService.updateMessage(message);
			throw e;
		} 
	}

	/**
	 * @param message
	 * @return the treatment result
	 * @throws BackOfficeUnrichableException 
	 * @throws LdapUserNotFoundException 
	 */
	public void treatApprovalMessage(final Message message) {
		try {
			/////check the quotas with the back office/////
			Integer nbToSend = message.getRecipients().size();
			String accountLabel = message.getUserAccountLabel();

			checkBackOfficeQuotas(nbToSend, accountLabel);

				// message is ready to be sent to the back office
				if (logger.isDebugEnabled()) {
					logger.debug("Setting to state WAINTING_FOR_SENDING message with ID (approval message case) : " + message.getId());
				}
				message.setStateAsEnum(MessageStatus.WAITING_FOR_SENDING);
				daoService.updateMessage(message);

				// launch ASAP the task witch manage the sms sending
				schedulerUtils.launchSuperviseSmsSending();

		} catch (UnknownIdentifierApplicationException e) {
			message.setStateAsEnum(MessageStatus.WS_ERROR);
			daoService.updateMessage(message);
		} catch (InsufficientQuotaException e) {
			message.setStateAsEnum(MessageStatus.WS_QUOTA_ERROR);
			daoService.updateMessage(message);		
		} catch (BackOfficeUnrichableException e) {
			message.setStateAsEnum(MessageStatus.WS_ERROR);
			daoService.updateMessage(message);
		} 
	}

	/**
	 * Used to send message in state waiting_for_sending.
	 */
	public void sendWaitingForSendingMessage() {
		// get all message ready to be sent
		final List<Message> messageList = daoService.getMessagesByState(MessageStatus.WAITING_FOR_SENDING);

		if (logger.isDebugEnabled()) {
			logger.debug("Found " + messageList.size() + " message(s) to send to the back office");
		}

		for (Message message : messageList) {
			if (logger.isDebugEnabled()) {
				logger.debug("Start managment of message with id : " + message.getId());
			}
			// customized all messages
			final List<CustomizedMessage> customizedMessageList = getCutomizedMessages(message);
			// get the associated customized group
			final String groupLabel = message.getGroupSender().getLabel();
			final CustomizedGroup cGroup = getRecurciveCustomizedGroupByLabel(groupLabel);

			// send the customized messages
			for (CustomizedMessage customizedMessage : customizedMessageList) {
				sendCustomizedMessages(customizedMessage);
				cGroup.setConsumedSms(cGroup.getConsumedSms() + 1);
				daoService.updateCustomizedGroup(cGroup);
			}

			// update the message status in DB
			message.setStateAsEnum(MessageStatus.SENT);

			// force commit to database. do not allow rollback otherwise the message will be sent again!
			DatabaseUtils.commit();
			DatabaseUtils.begin();

			//Deal with the emails
			if (message.getMail() != null) {
				sendMails(message);
			}

			daoService.updateMessage(message);

			if (logger.isDebugEnabled()) {
				logger.debug("End of managment of message with id : " + message.getId());
			}

		}
	}

	//////////////////////////////////////////////////////////////
	// Private tools methods
	//////////////////////////////////////////////////////////////
	/**
	 * send mail based on supervisors.
	 * @param supervisors
	 * @return
	 */
	private void sendMailsToSupervisors(final Set<Person> supervisors) {
		final List<String> toList = new LinkedList<String>();
		final List<String> uids = new LinkedList<String>();
		for (Person supervisor : supervisors) {
			uids.add(supervisor.getLogin());
		}
		final List<LdapUser> ldapUsers = new LinkedList<LdapUser>();
		ldapUsers.addAll(ldapUtils.getUsersByUids(uids));
		for (LdapUser ldapUser : ldapUsers) {
			if (logger.isDebugEnabled()) {
				logger.debug("supervisor login is :" + ldapUser.getId());
			}
			String mail = ldapUser.getAttribute(userEmailAttribute);
			if (mail != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("mail added to list :" + mail);
				}
				toList.add(mail);
			}
		}
		String subject = getI18nService().getString("MSG.SUBJECT.MAIL.TO.APPROVAL", 
				getI18nService().getDefaultLocale());
		String textBody = getI18nService().getString("MSG.TEXTBOX.MAIL.TO.APPROVAL", 
				getI18nService().getDefaultLocale());
		smtpServiceUtils.sendMessage(toList, null, subject, textBody);
	}

	/**
	 * @param message
	 * @param mailToSend
	 * @return the mail to apply to a message
	 */
	private Mail getMail(final Message message, final MailToSend mailToSend) {


		Mail mail = new Mail();

		//Mail subject
		String subject = mailToSend.getMailSubject();
		if (logger.isDebugEnabled()) {
			logger.debug("create the mail to store SUBJECT : " + subject);
		}
		mail.setSubject(subject);

		//Mail content
		String content = mailToSend.getMailContent();
		if (logger.isDebugEnabled()) {
			logger.debug("create the mail to store CONTENT : " + content);
		}
		mail.setContent(content);

		//Mail template
		String mailTemplate = mailToSend.getMailTemplate();
		if (mailTemplate != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("create the mail to store TEMPLATE : " + mailTemplate);
			}
			Integer idTemplate = Integer.parseInt(mailTemplate);
			Template template = daoService.getTemplateById(idTemplate);
			mail.setTemplate(template);
		}
		//Mail state
		mail.setStateAsEnum(MailStatus.WAITING);

		//Mail recipients
		final Set<MailRecipient> mailRecipients = new HashSet<MailRecipient>();
		MailRecipient mailRecipient = new MailRecipient();
		if (mailToSend.getIsMailToRecipients()) {
			final Set<Recipient> recipients = message.getRecipients();

			final List<String> uids = new LinkedList<String>();
			// all the ldap information are get from one request 
			for (Recipient recipient : recipients) {
				uids.add(recipient.getLogin());
			}
			List <LdapUser> ldapUsers = new LinkedList<LdapUser>();
			ldapUsers = ldapUtils.getUsersByUids(uids);

			for (LdapUser ldapUser : ldapUsers) {

				String login = ldapUser.getId();
				String addresse = ldapUser.getAttribute(userEmailAttribute);
				mailRecipient = daoService.getMailRecipientByAddress(addresse);
				if (mailRecipient == null) {
					mailRecipient = new MailRecipient(null, addresse, login);
				} else {
					// cas tordu d'un destinataire sans login remont�.
					if (mailRecipient.getLogin() == null) {
						mailRecipient.setLogin(login);
					}
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Add mail recipient from sms recipients: " + login + " [" + addresse + "]");
				}
				mailRecipients.add(mailRecipient);
			}
		}

		String[] otherRecipients = mailToSend.getMailOtherRecipients().split(",");
		for (String otherAdresse : otherRecipients) {
			if (!otherAdresse.equals("")) {
				mailRecipient = daoService.getMailRecipientByAddress(otherAdresse);
				if (mailRecipient == null) {
					mailRecipient = new MailRecipient(null, otherAdresse, null);
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Add mail recipient from other recipients: [" + otherAdresse + "]");
				}
				mailRecipients.add(mailRecipient);
			}
		}
		if (mailRecipients.size() > 0) {
			mail.setMailRecipients(mailRecipients);
		} else {
			mail = null;
		}
		return mail;
	}

	/**
	 * @param message
	 */
	public void sendMails(final Message message) {

		final List<String> toList = new LinkedList<String>();
		final Mail mail = message.getMail();

		if (logger.isDebugEnabled()) {
			logger.debug("sendMails");
		}
		if (mail != null) {
			if (logger.isDebugEnabled()) {
				logger.debug("sendMails mail not null");
			}
			//retrieve all informations from message (EXP_NOM, ...)
			final Set<MailRecipient> recipients = mail.getMailRecipients();
			final String expGroupName = message.getGroupSender().getLabel();
			final String expUid = message.getSender().getLogin();
			final String mailSubject = mail.getSubject();
			//the original content
			final String originalContent = mail.getContent();
			try {
				final String contentWithoutExpTags = customizer.customizeExpContent(
						originalContent, expGroupName, expUid );
				if (logger.isDebugEnabled()) {
					logger.debug("sendMails contentWithoutExpTags: " 
							+ contentWithoutExpTags);
				}
				for (MailRecipient recipient : recipients) {
					//the recipient uid
					final String destUid = recipient.getLogin();
					//the message is customized with user informations
					String customizedContentMail = null;

					customizedContentMail = customizer.customizeDestContent(
							contentWithoutExpTags, destUid);
					if (logger.isDebugEnabled()) {
						logger.debug("sendMails customizedContentMail: " 
								+ customizedContentMail);
					}

					String mailToAdd = recipient.getAddress();
					if (logger.isDebugEnabled()) {
						logger.debug("Mail sent to : " 
								+ mailToAdd);
					}
					if (mailToAdd != null ) {
						toList.add(mailToAdd);
						smtpServiceUtils.sendMessage(toList, null, 
								mailSubject, customizedContentMail);
						toList.clear();
					}
				}
				message.getMail().setStateAsEnum(MailStatus.SENT);
			} catch (LdapUserNotFoundException e1) {
				logger.debug("Unable to find the user with id : [" + expUid + "]", e1);
				message.getMail().setStateAsEnum(MailStatus.ERROR);
			} 
		}
	}

	/**
	 * @param message
	 * @return a supervisor list
	 */
	private Set<Person> getSupervisors(final Message message) {
		Set<Person> supervisors = new HashSet<Person>();

		if (message.getGroupRecipient() != null) { 
			String label = message.getGroupRecipient().getLabel();
			CustomizedGroup customizedGroup = getSupervisorCustomizedGroupByLabel(label);
			if (customizedGroup != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("Supervisor found from group : [" + customizedGroup.getLabel() + "]");
				}
				
				supervisors.addAll(customizedGroup.getSupervisors());
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("No supervisor found from groups. Use the default supervisor : [" + defaultSupervisorLogin + "]");
				}
				Person admin = daoService.getPersonByLogin(defaultSupervisorLogin);
				if (admin == null) {
					admin = new Person(null, defaultSupervisorLogin);
				}
				supervisors.add(admin);
			}
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug("Supervisor needed without a group. Use the default supervisor : [" + defaultSupervisorLogin + "]");
			}
			Person admin = daoService.getPersonByLogin(defaultSupervisorLogin);
			if (admin == null) {
				admin = new Person(null, defaultSupervisorLogin);
			}
			supervisors.add(admin);
		}

		return supervisors;
	}

	/**
	 * @param groupLabel
	 * @return the current customized group if it has supervisors or the first parent with supervisors.
	 */
	private CustomizedGroup getSupervisorCustomizedGroupByLabel(final String groupLabel) {
		if (logger.isDebugEnabled()) {
			logger.debug("getSupervisorCustomizedGroupByLabel for group [" + groupLabel + "]");
		}
		CustomizedGroup customizedGroup = getRecurciveCustomizedGroupByLabel(groupLabel);	
		if (customizedGroup != null) {
			if (customizedGroup.getSupervisors().isEmpty()) {
				if (logger.isDebugEnabled()) {
					logger.debug("Customized group without supervisor found : [" + customizedGroup.getLabel() + "]");
				}
				String portalGroupId = customizedGroup.getLabel();
				String parentGroup = ldapUtils.getParentGroupIdByGroupId(portalGroupId);
				if (parentGroup != null) {
					customizedGroup = getRecurciveCustomizedGroupByLabel(parentGroup);
				} else {
					// if no parent group is found, a null customized group is returned. 
					customizedGroup = null;
				}
			}
		}
		return customizedGroup;
	}

	/**
	 * @param uiRecipients
	 * @return the recipients list.
	 */
	private Set<Recipient> getRecipients(final List<UiRecipient> uiRecipients, final Service service) {

		Set<Recipient> recipients = new HashSet<Recipient>();
		Recipient recipient;

		// determines all the recipients.
		for (UiRecipient uiRecipient : uiRecipients) {

			// single users and phone numbers can be directly added to the message.
			if (!uiRecipient.getClass().equals(GroupRecipient.class)) {

				// check if the recipient is already in the database. 
				recipient = daoService.getRecipientByPhone(uiRecipient.getPhone());

				if (recipient == null) {	
					recipient = new Recipient(null, uiRecipient.getPhone(), uiRecipient.getLogin());
					daoService.addRecipient(recipient);
				}
				// the recipient is added.
				if (!recipients.contains(recipient)) {
					recipients.add(recipient);
				}

			} else {
				String serviceKey = null;
				if (service != null) {
					serviceKey = service.getKey();
				}
				// Group users are search from the portal.
				String groupName = uiRecipient.getDisplayName();
				List<LdapUser> groupUsers = getUsersByGroup(groupName,serviceKey);
				// users are filtered to keep only service compliant ones.
				List<LdapUser> filteredUsers = filterUsers(groupUsers, serviceKey);
				//users are added to the recipient list.
				for (LdapUser ldapUser : filteredUsers) {
					String phone;
					phone = ldapUser.getAttribute(userPagerAttribute);

					recipient = daoService.getRecipientByPhone(phone);
					if (recipient == null) {	
						recipient = new Recipient(null, phone,
								ldapUser.getId());
						daoService.addRecipient(recipient);
					}
					// the recipient is added to the list.
					if (!recipients.contains(recipient)) {
						recipients.add(recipient);
					}

				}

			}
		}

		return recipients;
	}

	/**
	 * @param groupName
	 * @param serviceKey 
	 * @return the user list.
	 */
	public List<LdapUser> getUsersByGroup(final String groupName, String serviceKey) {
		if (logger.isDebugEnabled()) {
			logger.debug("Search users for group [" + groupName + "]");
		}
		//get the recipient group hierarchy
		PortalGroupHierarchy groupHierarchy = ldapUtils.getPortalGroupHierarchyByGroupName(groupName);
		//get all users from the group hierarchy
		List<LdapUser> members = getMembers(groupHierarchy, serviceKey);

		if (logger.isDebugEnabled()) {
			logger.debug("Number of ldap users found : " + members.size());
		}
		return members;
	}


	/**
	 * @param groupHierarchy
	 * @param serviceKey 
	 * @return the list of the unique sub-members of a group (recursive)
	 */
	private List<LdapUser> getMembers(final PortalGroupHierarchy groupHierarchy, String serviceKey) {
		final PortalGroup currentGroup = groupHierarchy.getGroup();
		if (logger.isDebugEnabled()) {
			logger.debug("Search users for subgroup [" + currentGroup.getName()
				     + "] [" + currentGroup.getId() + "]");
		}
		final List<PortalGroupHierarchy> childs = groupHierarchy.getSubHierarchies();
		List<LdapUser> members = new LinkedList<LdapUser>();
		//get the corresponding ldap group to extract members

		try {
			String idFromPortal = currentGroup.getId();
			String groupStoreId = StringUtils.split(idFromPortal,".")[1];
			GroupDefinition gd = smsuPersonAttributesGroupStore.getGroupDefinition(groupStoreId);
			if (gd != null) {
				if (logger.isDebugEnabled()) {
					logger.debug("search members");
				}
				members = ldapUtils.getMembers(gd, userTermsOfUseAttribute, serviceKey);
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("No group definition found");
				}
			}
		} catch (Throwable e) {
			logger.debug(e.getMessage());
		}

		Boolean isGroupLeaf = true;
		if (childs != null) {
			isGroupLeaf = false;
		}
		if (!isGroupLeaf) {
			for (PortalGroupHierarchy child : childs) {
				List<LdapUser> subMembers = getMembers(child, serviceKey);
				if (logger.isDebugEnabled()) {
					logger.debug("Merge members for group " + child.getGroup().getName());
				}
				members = mergeUserLists(members, subMembers);
			}
		}

		return members;
	}


	/**
	 * @param source
	 * @param toMerge
	 * @return the merged list
	 */
	private List<LdapUser> mergeUserLists(final List<LdapUser> source, final List<LdapUser> toMerge) {
		final List<LdapUser> finalList = source;
		for (LdapUser sToMerge : toMerge) {
			if (!finalList.contains(sToMerge)) {
				finalList.add(sToMerge);
				if (logger.isDebugEnabled()) {
					logger.debug("Element [" + sToMerge + "] merged to the source list");
				}
			}
		}
		return finalList;
	}

	/**
	 * @param service
	 * @return the filtered list of users
	 */
	private List<LdapUser> filterUsers(final List<LdapUser> users, final String service) {
		if (logger.isDebugEnabled()) {
			logger.debug("Filtering users for service [" + service + "]");
		}
		List<LdapUser> filteredUsers = new ArrayList<LdapUser>();

		for (LdapUser user : users) {
			List<String> userTermsOfUse = user.getAttributes(userTermsOfUseAttribute);
			if (userTermsOfUse.contains(cgKeyName)) {
				if (service != null) {
					if (logger.isDebugEnabled()) {
						logger.debug("Service filter activated");
					}
					if (userTermsOfUse.contains(service)) {
						filteredUsers.add(user);
					}
				} else {
					if (logger.isDebugEnabled()) {
						logger.debug("No service filter");
					}
					filteredUsers.add(user);
				}
			} else {
				if (logger.isDebugEnabled()) {
					logger.debug("CG not validated, user : " + user.getId());
				}
			}
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Number of filtered users : " + filteredUsers.size());
		}
		return filteredUsers;
	}

	/**
	 * get the message template.
	 */
	private Template getMessageTemplate(final String strTemplate) {
		Integer iTemplate = Integer.parseInt(strTemplate);
		return daoService.getTemplateById(iTemplate);		 
	}

	/**
	 * get the message sender.
	 */
	private Person getSender(final String strLogin) {
		Person sender = daoService.getPersonByLogin(strLogin);
		if (sender == null) {
			sender = new Person();
			sender.setLogin(strLogin);
		}
		return sender;
	}

	/**
	 * @param message
	 * @return the message workflow state.
	 */
	private MessageStatus getWorkflowState(final Message message) {
		final Integer nbRecipients = message.getRecipients().size();
		if (logger.isDebugEnabled()) {
			logger.debug(nbRecipients.toString());
		}
		final String groupLabel = message.getGroupSender().getLabel();
		if (logger.isDebugEnabled()) {
			logger.debug("sender group label : ");
			logger.debug(groupLabel);
		}
		final CustomizedGroup cGroup = getRecurciveCustomizedGroupByLabel(groupLabel);
		if (logger.isDebugEnabled()) {
			logger.debug("checkFrontOfficeQuota");
		}
		final Boolean foQuota = checkFrontOfficeQuota(message, cGroup);
		if (logger.isDebugEnabled()) {
			logger.debug("checkFrontOfficeQuota result :");
			logger.debug(foQuota.toString());
		}
		if (nbRecipients.equals(0)) {
			return 	MessageStatus.NO_RECIPIENT_FOUND;
		}
		if (!foQuota) {
			return MessageStatus.FO_QUOTA_ERROR;
		}
		final Boolean foNbMax = checkMaxSmsGroupQuota(message, cGroup);
		if (!foNbMax) {
			return MessageStatus.FO_NB_MAX_CUSTOMIZED_GROUP_ERROR;
		}
		if (message.getRecipients().size() >= nbMaxSmsBeforeSupervising) {
			return MessageStatus.WAITING_FOR_APPROVAL;
		}
		if (message.getGroupRecipient() != null) {
			return MessageStatus.WAITING_FOR_APPROVAL;
		}
		return MessageStatus.IN_PROGRESS;
	}

	/**
	 * @param uiRecipients
	 * @return the recipient group, or null.
	 */
	private BasicGroup getGroupRecipient(final List<UiRecipient> uiRecipients) {
		for (UiRecipient uiRecipient : uiRecipients) {
			if (uiRecipient.getClass().equals(GroupRecipient.class)) {
				String label = uiRecipient.getDisplayName();
				PortalGroup pGroup = ldapUtils.getPortalGroupByName(label);
				String portalId = pGroup.getId();
				BasicGroup groupRecipient = daoService.getGroupByLabel(portalId);
				if (groupRecipient == null) {
					groupRecipient = new BasicGroup();
					groupRecipient.setLabel(portalId);
				}
				return groupRecipient;
			}
		}
		return null;
	}

	/**
	 * Customized the messages.
	 * @param message
	 * @return
	 */
	private List<CustomizedMessage> getCutomizedMessages(final Message message) {
		final List<CustomizedMessage> customizedMessageList = new ArrayList<CustomizedMessage>();
		final Set<Recipient> recipients = message.getRecipients();
		//the account label
		final String labelAccount = message.getUserAccountLabel();
		//the group id and label
		final Integer bgrId = message.getGroupSender().getId();
		final String expGroupName = message.getGroupSender().getLabel();
		//the service id
		final Integer svcId;
		if (message.getService() != null) {
			svcId = message.getService().getId();
		} else {
			svcId = null;
		}
		//the message id
		final Integer msgId = message.getId();
		//the sender id, name and phone
		final Integer perId = message.getSender().getId();
		final String expUid = message.getSender().getLogin();
		//the original content
		final String originalContent = message.getContent();
		try {
			final String contentWithoutExpTags = customizer.customizeExpContent(originalContent, 
					expGroupName, expUid );
			if (logger.isDebugEnabled()) {
				logger.debug(contentWithoutExpTags);
			}
			for (Recipient recipient : recipients) {
				//the phone number
				final String smsPhone = recipient.getPhone();
				//the recipient uid
				final String destUid = recipient.getLogin();
				//the message is customized with user informations
				String msgContent = customizer.customizeDestContent(contentWithoutExpTags, destUid);
				if (msgContent.length() > smsMaxSize) {
					msgContent = msgContent.substring(0, smsMaxSize);
				}
				// create the final message with all data needed to send it
				final CustomizedMessage customizedMessage = new CustomizedMessage();
				customizedMessage.setMessageId(msgId);
				customizedMessage.setSenderId(perId);
				customizedMessage.setGroupSenderId(bgrId);
				customizedMessage.setServiceId(svcId);
				customizedMessage.setRecipiendPhoneNumber(smsPhone);
				customizedMessage.setUserAccountLabel(labelAccount);
				customizedMessage.setMessage(msgContent);
				customizedMessageList.add(customizedMessage);
			}
		} catch (LdapUserNotFoundException e1) {
			logger.debug("Unable to find the user with id : [" + expUid + "]", e1);
		} 
		return customizedMessageList;
	}

	/**
	 * Send the customized message to the back office.
	 * @param customizedMessage
	 */
	private void sendCustomizedMessages(final CustomizedMessage customizedMessage) {
		final Integer messageId = customizedMessage.getMessageId();
		final Integer senderId = customizedMessage.getSenderId();
		final Integer groupSenderId = customizedMessage.getGroupSenderId();
		final Integer serviceId = customizedMessage.getServiceId();
		final String recipiendPhoneNumber = customizedMessage.getRecipiendPhoneNumber();
		final String userLabelAccount = customizedMessage.getUserAccountLabel();
		final String message = customizedMessage.getMessage();
		if (logger.isDebugEnabled()) {
			logger.debug("Sending to back office message with : " + 
				     " - message id = " + messageId + 
				     " - sender id = " + senderId + 
				     " - group sender id = " + groupSenderId + 
				     " - service id = " + serviceId + 
				     " - recipient phone number = " + recipiendPhoneNumber + 
				     " - user label account = " + userLabelAccount + 
				     " - message = " + message);
		}
		// send the message to the back office

		sendSmsClient.sendSMS(messageId, senderId, groupSenderId, serviceId, 
				recipiendPhoneNumber,	userLabelAccount, message);

	}

	private Boolean checkMaxSmsGroupQuota(final Message message, final CustomizedGroup cGroup) {
		Boolean quotaOK = false;
		final Long quotaOrder = cGroup.getQuotaOrder();
		final Integer nbToSend = message.getRecipients().size();
		if (nbToSend <= quotaOrder) {
			quotaOK = true;
		} else {
			final String mess = 
			    "Erreur de nombre maximum de sms par envoi pour le groupe d'envoi [" + 
			    message.getGroupSender().getLabel() + 
			    "] et groupe associ� [" + cGroup.getLabel() + 
			    "]. Essai d'envoi de " + nbToSend + " message(s), nombre max par envoi = " + quotaOrder;
			logger.warn(mess);
		}
		return quotaOK;
	}

	/**
	 * @param message
	 * @param cGroup
	 * @return true if the quota is OK
	 */
	private Boolean checkFrontOfficeQuota(final Message message, final CustomizedGroup cGroup) {
		final Integer nbToSend = message.getRecipients().size();

		if (logger.isDebugEnabled()) {
			final String mess = 
			    "V�rification du quota front office pour le groupe d'envoi [" + 
			    message.getGroupSender().getLabel() + 
			    "] et groupe associ� [" + cGroup.getLabel() + 
			    "]. Essai d'envoi de " + nbToSend + " message(s), quota = " + cGroup.getQuotaSms() + 
			    " , consomm� = " + cGroup.getConsumedSms();
			logger.warn(mess);
		}
		if (cGroup.checkQuotaSms(nbToSend)) {
			return true;
		} else {
			final String mess = 
			    "Erreur de quota pour le groupe d'envoi [" + message.getGroupSender().getLabel() + 
			    "] et groupe associ� [" + cGroup.getLabel() + "]. Essai d'envoi de " + nbToSend + 
			    " message(s), quota = " + cGroup.getQuotaSms() + " , consomm� = " + cGroup.getConsumedSms();
			logger.warn(mess);
			return false;
		}
	}
	/**
	 * @return quotasOk
	 */ 
	private void checkBackOfficeQuotas(final Integer nbToSend, final String accountLabel) 
	throws BackOfficeUnrichableException, UnknownIdentifierApplicationException,
	InsufficientQuotaException {
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Request for WS SendSms method isQuotaOk with parameters \n" 
						+ "nbToSend = " + nbToSend + "\n" 
						+ "accountLabel = " + accountLabel);
			}

			sendSmsClient.mayCreateAccountCheckQuotaOk(nbToSend, accountLabel);

			if (logger.isDebugEnabled()) {
				logger.debug("checkQuotaOk: quota is ok to send all our messages"); 
			}

		} catch (UnknownIdentifierApplicationException e) {
			throw new UnknownIdentifierApplicationException(e.getMessage());
		} catch (InsufficientQuotaException e) {
			throw new InsufficientQuotaException(e.getMessage());
		} catch (Exception e) {
			logger.error("Unable to connect to the back office : ", e);
			throw new BackOfficeUnrichableException();
		}
	}

	/**
	 * @param userGroup
	 * @return the sender group.
	 */
	private BasicGroup getGroupSender(final String userGroup) {
		// the sender group is set
		BasicGroup basicGroupSender = daoService.getGroupByLabel(userGroup);
		if (basicGroupSender == null) {
			basicGroupSender = new BasicGroup();
			basicGroupSender.setLabel(userGroup);
		}
		return basicGroupSender;
	}
	/**
	 * @param userGroup
	 * @return an account.
	 */
	private Account getAccount(final String userGroup) {
		CustomizedGroup groupSender = getRecurciveCustomizedGroupByLabel(userGroup);
		Account count;
		if (groupSender == null) {
			//Default account
			logger.warn("No account found. The default account is used : " + defaultAccount);
			count = daoService.getAccountByLabel(defaultAccount); 
		} else {
			count = groupSender.getAccount();
		}
		// the account is set
		return count;
	}

	/**
	 * @param groupId
	 * @return the customized group corresponding to a group
	 */
	private CustomizedGroup getRecurciveCustomizedGroupByLabel(final String portalGroupId) {
		if (logger.isDebugEnabled()) {
			logger.debug("Search the cutomised group associated to the group : [" + portalGroupId + "]");
		}
		//search the customized group from the data base
		CustomizedGroup groupSender = daoService.getCustomizedGroupByLabel(portalGroupId);
		if (groupSender == null) {
			if (logger.isDebugEnabled()) {
				logger.debug("Customized group not found : " + portalGroupId);
			}
			//get the parent group
			String parentGroup = ldapUtils.getParentGroupIdByGroupId(portalGroupId);
			if (parentGroup != null) {
				//if a parent group is found, search the corresponding customized group
				groupSender = getRecurciveCustomizedGroupByLabel(parentGroup);
			}
		}
		return groupSender;
	}


	/**
	 * @param id
	 * @return the service.
	 */
	private Service getService(final Integer id) {
		Service service = null;
		if (id != null) {
			service = daoService.getServiceById(id);
		}
		return service;
	}

	///////////////////////////////////
	// Getter and Setter of smsMaxSize
	///////////////////////////////////
	/**
	 * @param smsMaxSize
	 */
	public void setSmsMaxSize(final Integer smsMaxSize) {
		this.smsMaxSize = smsMaxSize;
	}

	/**
	 * @return smsMaxSize
	 */
	public Integer getSmsMaxSize() {
		return smsMaxSize;
	}

	//////////////////////////////
	//  setters for spring objects
	//////////////////////////////
	/**
	 * @param sendSmsClient
	 */
	public void setSendSmsClient(final SendSmsClient sendSmsClient) {
		this.sendSmsClient = sendSmsClient;
	}

	/**
	 * Standard setter used by spring.
	 * @param schedulerUtils
	 */
	public void setSchedulerUtils(final SchedulerUtils schedulerUtils) {
		this.schedulerUtils = schedulerUtils;
	}

	/**
	 * Standard setter used by spring.
	 * @param smtpServiceUtils
	 */
	public void setSmtpServiceUtils(final SmtpServiceUtils smtpServiceUtils) {
		this.smtpServiceUtils = smtpServiceUtils;
	}

	/**
	 * Standard setter used by spring.
	 * @param ldapUtils
	 */
	public void setLdapUtils(final LdapUtils ldapUtils) {
		this.ldapUtils = ldapUtils;
	}

	///////////////////////////////////
	// Getter and Setter of i18nService
	///////////////////////////////////
	/**
	 * Set the i18nService.
	 * @param i18nService
	 */
	public void setI18nService(final I18nService i18nService) {
		this.i18nService = i18nService;
	}

	/**
	 * @return the i18nService.
	 */
	public I18nService getI18nService() {
		return i18nService;
	}

	///////////////////////////////////////////////
	// Getter and Setter of defaultSupervisorLogin
	///////////////////////////////////////////////
	/**
	 * @return the defaultSupervisorLogin.
	 */
	public String getDefaultSupervisorLogin() {
		return defaultSupervisorLogin;
	}

	/**
	 * @param defaultSupervisorLogin
	 */
	public void setDefaultSupervisorLogin(final String defaultSupervisorLogin) {
		this.defaultSupervisorLogin = defaultSupervisorLogin;
	}

	//////////////////////////////////
	// Getter and Setter of daoService
	//////////////////////////////////
	/**
	 * @return the daoService.
	 */
	public DaoService getDaoService() {
		return daoService;
	}

	/**
	 * @param daoService
	 */
	public void setDaoService(final DaoService daoService) {
		this.daoService = daoService;
	}

	//////////////////////////////////////////////////////////////
	// Getter and Setter of nbMaxSmsBeforeSupervising
	//////////////////////////////////////////////////////////////
	/**
	 * @return nbMaxSmsBeforeSupervising
	 */
	public Integer getNbMaxSmsBeforeSupervising() {
		return nbMaxSmsBeforeSupervising;
	}

	/**
	 * @param nbMaxSmsBeforeSupervising
	 */
	public void setNbMaxSmsBeforeSupervising(final Integer nbMaxSmsBeforeSupervising) {
		this.nbMaxSmsBeforeSupervising = nbMaxSmsBeforeSupervising;
	}

	//////////////////////////////////////////////////////////////
	// Getter and Setter of customizer
	//////////////////////////////////////////////////////////////

	/**
	 * @param customizer
	 */
	public void setCustomizer(final ContentCustomizationManager customizer) {
		this.customizer = customizer;
	}

	/**
	 * @return the ContentCustomizationManager
	 */
	public ContentCustomizationManager getCustomizer() {
		return customizer;
	}

	//////////////////////////////////////////////////////////////
	// Setter of defaultAccount
	//////////////////////////////////////////////////////////////
	/**
	 * @param defaultAccount
	 */
	public void setDefaultAccount(final String defaultAccount) {
		this.defaultAccount = defaultAccount;
	}

	/**
	 * @return userEmailAttribute
	 */
	public String getUserEmailAttribute() {
		return userEmailAttribute;
	}

	/**
	 * @param userEmailAttribute
	 */
	public void setUserEmailAttribute(final String userEmailAttribute) {
		this.userEmailAttribute = userEmailAttribute;
	}

	public void setSmsuPersonAttributesGroupStore(
			final SmsuPersonAttributesGroupStore smsuPersonAttributesGroupStore) {
		this.smsuPersonAttributesGroupStore = smsuPersonAttributesGroupStore;
	}

	public SmsuPersonAttributesGroupStore getSmsuPersonAttributesGroupStore() {
		return smsuPersonAttributesGroupStore;
	}

	/**
	 * @return the cg Key Name
	 */
	public String getCgKeyName() {
		return cgKeyName;
	}

	/**
	 * @param cgKeyName
	 */
	public void setCgKeyName(final String cgKeyName) {
		this.cgKeyName = cgKeyName;
	}

	public void setUserPagerAttribute(final String userPagerAttribute) {
		this.userPagerAttribute = userPagerAttribute;
	}

	public String getUserPagerAttribute() {
		return userPagerAttribute;
	}

	/**
	 * @param userTermsOfUseAttribute to set
	 */
	public void setUserTermsOfUseAttribute(final String userTermsOfUseAttribute) {
		this.userTermsOfUseAttribute = userTermsOfUseAttribute;
	}


}