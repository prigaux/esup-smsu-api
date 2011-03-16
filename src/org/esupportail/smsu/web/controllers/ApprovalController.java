package org.esupportail.smsu.web.controllers;

import org.esupportail.smsu.domain.beans.User;
import org.esupportail.smsu.domain.beans.fonction.FonctionName;
import org.esupportail.smsu.web.beans.ApprovalPaginator;
import org.esupportail.smsu.web.beans.UIMessage;

/**
 * A bean to manage files.
 */
public class ApprovalController extends AbstractContextAwareController {

	/**
	 * The serialization id.
	 */
	private static final long serialVersionUID = -1149078913806276304L;
	
	/**
	 * The approval paginator.
	 */
	private ApprovalPaginator paginator;
		
	/**
	 * The message.
	 */
	 private UIMessage message;
				 
	//////////////////////////////////////////////////////////////
	// Constructors
	//////////////////////////////////////////////////////////////
	/**
	 * Bean constructor.
	 */
	public ApprovalController() {
		super();
	}
	
	
	//////////////////////////////////////////////////////////////
	// Init method
	//////////////////////////////////////////////////////////////
	/**
	 * @return true if the current user is allowed to view the page.
	 */
	public boolean isPageAuthorized() {
		//an access control is required for this page.
		User currentUser = getCurrentUser();
		if (currentUser == null) {
			return false;
		}
		return currentUser.getFonctions().contains(FonctionName.FCTN_APPROBATION_ENVOI.name());
	}
	
	/**
	 * JSF callback.
	 * @return A String.
	 * @throws LdapUserNotFoundException 
	 */
	public String enter()  {
		if (!isPageAuthorized()) {
			addUnauthorizedActionMessage();
			return null;
		}
		// initialize data in the page
		init();
		return "navigationApproveSMS";
	}
	
	/**
	 * initialize data in the page.
	 * @throws LdapUserNotFoundException 
	 */
	private void init()  {
		User currentUser = getCurrentUser();
		if (currentUser != null) {
		paginator = new ApprovalPaginator(getDomainService(), currentUser.getId());
		} else {
		paginator = new ApprovalPaginator(getDomainService(), null);
		}
		
	}

	/**
	 * @see org.esupportail.smsu.web.controllers.AbstractContextAwareController#reset()
	 */
	@Override
	public void reset() {
		super.reset();
		User currentUser = getCurrentUser();
		if (currentUser != null) {
		paginator = new ApprovalPaginator(getDomainService(), currentUser.getId());
		} else {
		paginator = new ApprovalPaginator(getDomainService(), null);
		}
	}
	//////////////////////////////////////////////////////////////
	// Principal methods
	//////////////////////////////////////////////////////////////
	/**
	 * For treatments.
	 * @return the paginator
	 */
	public ApprovalPaginator getPaginator() {
		return paginator;
	}

	/**
	 * validate action and reload paginator.
	 * @return A String
	 */
	public String validate()  {
		getDomainService().treatUIMessage(message);
		reset();
		return "navigationApproveSMS";
	}
	
	/**
	 * cancel action and reload paginator.
	 * @return A String
	 */
	public String cancel()  {
		getDomainService().updateUIMessage(message);
		reset();
		return "navigationApproveSMS";
	}
	
	//////////////////////////////////////////////////////////////
	// Setter and Getter for message
	//////////////////////////////////////////////////////////////
	/**
	 * @param message the message to set
	 */
	public void setMessage(final UIMessage message) {
		this.message = message;
	}

	/**
	 * @return the message
	 */
	public UIMessage getMessage() {
		return message;
	}

	//////////////////////////////////////////////////////////////
	// Others
	//////////////////////////////////////////////////////////////
	/**
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return getClass().getSimpleName() + "#" + hashCode();
	}

	
	
	
}