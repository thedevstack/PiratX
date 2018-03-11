package eu.siacs.conversations.xmpp.stanzas;

import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.xml.Element;
import rocks.xmpp.addr.Jid;

public class AbstractStanza extends Element {

	protected AbstractStanza(final String name) {
		super(name);
	}

	public Jid getTo() {
		return getAttributeAsJid("to");
	}

	public Jid getFrom() {
		return getAttributeAsJid("from");
	}

	public void setTo(final Jid to) {
		if (to != null) {
			setAttribute("to", to.toEscapedString());
		}
	}

	public void setFrom(final Jid from) {
		if (from != null) {
			setAttribute("from", from.toEscapedString());
		}
	}

	public boolean fromServer(final Account account) {
		return getFrom() == null
			|| getFrom().equals(Jid.of(account.getServer()))
			|| getFrom().equals(account.getJid().asBareJid())
			|| getFrom().equals(account.getJid());
	}

	public boolean toServer(final Account account) {
		return getTo() == null
			|| getTo().equals(Jid.of(account.getServer()))
			|| getTo().equals(account.getJid().asBareJid())
			|| getTo().equals(account.getJid());
	}

	public boolean fromAccount(final Account account) {
		return getFrom() != null && getFrom().asBareJid().equals(account.getJid().asBareJid());
	}
}
