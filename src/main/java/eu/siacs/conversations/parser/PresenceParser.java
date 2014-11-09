package eu.siacs.conversations.parser;

import eu.siacs.conversations.crypto.PgpEngine;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.generator.PresenceGenerator;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnPresencePacketReceived;
import eu.siacs.conversations.xmpp.jid.Jid;
import eu.siacs.conversations.xmpp.stanzas.PresencePacket;

public class PresenceParser extends AbstractParser implements
		OnPresencePacketReceived {

	public PresenceParser(XmppConnectionService service) {
		super(service);
	}

	public void parseConferencePresence(PresencePacket packet, Account account) {
		PgpEngine mPgpEngine = mXmppConnectionService.getPgpEngine();
		if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
			final Conversation muc = packet.getFrom() == null ? null : mXmppConnectionService.find(
                    account,
                    packet.getFrom().toBareJid());
			if (muc != null) {
				boolean before = muc.getMucOptions().online();
				muc.getMucOptions().processPacket(packet, mPgpEngine);
				if (before != muc.getMucOptions().online()) {
					mXmppConnectionService.updateConversationUi();
				}
				mXmppConnectionService.getAvatarService().clear(muc);
			}
		} else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
			final Conversation muc = mXmppConnectionService.find(account,
                    packet.getFrom().toBareJid());
			if (muc != null) {
				boolean before = muc.getMucOptions().online();
				muc.getMucOptions().processPacket(packet, mPgpEngine);
				if (before != muc.getMucOptions().online()) {
					mXmppConnectionService.updateConversationUi();
				}
				mXmppConnectionService.getAvatarService().clear(muc);
			}
		}
	}

	public void parseContactPresence(PresencePacket packet, Account account) {
		PresenceGenerator mPresenceGenerator = mXmppConnectionService
				.getPresenceGenerator();
		if (packet.getFrom() == null) {
			return;
		}
        final Jid from = packet.getFrom();
		String type = packet.getAttribute("type");
		if (from.toBareJid().equals(account.getJid().toBareJid())) {
			if (!from.isBareJid()) {
				if (type == null) {
					account.updatePresence(from.getResourcepart(),
							Presences.parseShow(packet.findChild("show")));
				} else if (type.equals("unavailable")) {
					account.removePresence(from.getResourcepart());
					account.deactivateGracePeriod();
				}
			}
		} else {
			Contact contact = account.getRoster().getContact(packet.getFrom());
			if (type == null) {
				String presence;
				if (!from.isBareJid()) {
					presence = from.getResourcepart();
				} else {
					presence = "";
				}
				int sizeBefore = contact.getPresences().size();
				contact.updatePresence(presence,
						Presences.parseShow(packet.findChild("show")));
				PgpEngine pgp = mXmppConnectionService.getPgpEngine();
				if (pgp != null) {
					Element x = packet.findChild("x", "jabber:x:signed");
					if (x != null) {
						Element status = packet.findChild("status");
						String msg;
						if (status != null) {
							msg = status.getContent();
						} else {
							msg = "";
						}
						contact.setPgpKeyId(pgp.fetchKeyId(account, msg,
								x.getContent()));
					}
				}
				boolean online = sizeBefore < contact.getPresences().size();
				updateLastseen(packet, account, true);
				mXmppConnectionService.onContactStatusChanged
						.onContactStatusChanged(contact, online);
			} else if (type.equals("unavailable")) {
				if (from.isBareJid()) {
					contact.clearPresences();
				} else {
					contact.removePresence(from.getResourcepart());
				}
				mXmppConnectionService.onContactStatusChanged
						.onContactStatusChanged(contact, false);
			} else if (type.equals("subscribe")) {
				if (contact.getOption(Contact.Options.PREEMPTIVE_GRANT)) {
					mXmppConnectionService.sendPresencePacket(account,
							mPresenceGenerator.sendPresenceUpdatesTo(contact));
				} else {
					contact.setOption(Contact.Options.PENDING_SUBSCRIPTION_REQUEST);
				}
			}
			Element nick = packet.findChild("nick",
					"http://jabber.org/protocol/nick");
			if (nick != null) {
				contact.setPresenceName(nick.getContent());
			}
		}
		mXmppConnectionService.updateRosterUi();
	}

	@Override
	public void onPresencePacketReceived(Account account, PresencePacket packet) {
		if (packet.hasChild("x", "http://jabber.org/protocol/muc#user")) {
			this.parseConferencePresence(packet, account);
		} else if (packet.hasChild("x", "http://jabber.org/protocol/muc")) {
			this.parseConferencePresence(packet, account);
		} else {
			this.parseContactPresence(packet, account);
		}
	}

}
