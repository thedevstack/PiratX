package eu.siacs.conversations.entities;

import android.content.ContentValues;
import android.database.Cursor;
import android.os.SystemClock;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.SessionID;
import net.java.otr4j.session.SessionImpl;
import net.java.otr4j.session.SessionStatus;

import org.json.JSONException;
import org.json.JSONObject;

import java.security.interfaces.DSAPublicKey;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jid.InvalidJidException;
import eu.siacs.conversations.xmpp.jid.Jid;

public class Conversation extends AbstractEntity {
	public static final String TABLENAME = "conversations";

	public static final int STATUS_AVAILABLE = 0;
	public static final int STATUS_ARCHIVED = 1;
	public static final int STATUS_DELETED = 2;

	public static final int MODE_MULTI = 1;
	public static final int MODE_SINGLE = 0;

	public static final String NAME = "name";
	public static final String ACCOUNT = "accountUuid";
	public static final String CONTACT = "contactUuid";
	public static final String CONTACTJID = "contactJid";
	public static final String STATUS = "status";
	public static final String CREATED = "created";
	public static final String MODE = "mode";
	public static final String ATTRIBUTES = "attributes";

	public static final String ATTRIBUTE_NEXT_ENCRYPTION = "next_encryption";
	public static final String ATTRIBUTE_MUC_PASSWORD = "muc_password";
	public static final String ATTRIBUTE_MUTED_TILL = "muted_till";

	private String name;
	private String contactUuid;
	private String accountUuid;
	private Jid contactJid;
	private int status;
	private long created;
	private int mode;

	private JSONObject attributes = new JSONObject();

	private Jid nextCounterpart;

	protected ArrayList<Message> messages = new ArrayList<>();
	protected Account account = null;

	private transient SessionImpl otrSession;

	private transient String otrFingerprint = null;

	private String nextMessage;

	private transient MucOptions mucOptions = null;

	// private transient String latestMarkableMessageId;

	private byte[] symmetricKey;

	private Bookmark bookmark;

	public Conversation(final String name, final Account account, final Jid contactJid,
			final int mode) {
		this(java.util.UUID.randomUUID().toString(), name, null, account
				.getUuid(), contactJid, System.currentTimeMillis(),
				STATUS_AVAILABLE, mode, "");
		this.account = account;
	}

	public Conversation(final String uuid, final String name, final String contactUuid,
			final String accountUuid, final Jid contactJid, final long created, final int status,
			final int mode, final String attributes) {
		this.uuid = uuid;
		this.name = name;
		this.contactUuid = contactUuid;
		this.accountUuid = accountUuid;
		this.contactJid = contactJid;
		this.created = created;
		this.status = status;
		this.mode = mode;
		try {
			this.attributes = new JSONObject(attributes == null ? "" : attributes);
		} catch (JSONException e) {
			this.attributes = new JSONObject();
		}
	}

	public List<Message> getMessages() {
		return messages;
	}

	public boolean isRead() {
        return (this.messages == null) || (this.messages.size() == 0) || this.messages.get(this.messages.size() - 1).isRead();
    }

	public void markRead() {
		if (this.messages == null) {
			return;
		}
		for (int i = this.messages.size() - 1; i >= 0; --i) {
			if (messages.get(i).isRead()) {
				break;
			}
			this.messages.get(i).markRead();
		}
	}

	public String getLatestMarkableMessageId() {
		if (this.messages == null) {
			return null;
		}
		for (int i = this.messages.size() - 1; i >= 0; --i) {
			if (this.messages.get(i).getStatus() <= Message.STATUS_RECEIVED
					&& this.messages.get(i).markable) {
				if (this.messages.get(i).isRead()) {
					return null;
				} else {
					return this.messages.get(i).getRemoteMsgId();
				}
			}
		}
		return null;
	}

	public Message getLatestMessage() {
		if ((this.messages == null) || (this.messages.size() == 0)) {
			Message message = new Message(this, "", Message.ENCRYPTION_NONE);
			message.setTime(getCreated());
			return message;
		} else {
			Message message = this.messages.get(this.messages.size() - 1);
			message.setConversation(this);
			return message;
		}
	}

	public void setMessages(ArrayList<Message> msgs) {
		this.messages = msgs;
	}

	public String getName() {
		if (getMode() == MODE_MULTI && getMucOptions().getSubject() != null) {
			return getMucOptions().getSubject();
		} else if (getMode() == MODE_MULTI && bookmark != null
				&& bookmark.getName() != null) {
			return bookmark.getName();
		} else {
			return this.getContact().getDisplayName();
		}
	}

	public String getProfilePhotoString() {
		return this.getContact().getProfilePhoto();
	}

	public String getAccountUuid() {
		return this.accountUuid;
	}

	public Account getAccount() {
		return this.account;
	}

	public Contact getContact() {
		return this.account.getRoster().getContact(this.contactJid);
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public Jid getContactJid() {
		return this.contactJid;
	}

	public int getStatus() {
		return this.status;
	}

	public long getCreated() {
		return this.created;
	}

	public ContentValues getContentValues() {
		ContentValues values = new ContentValues();
		values.put(UUID, uuid);
		values.put(NAME, name);
		values.put(CONTACT, contactUuid);
		values.put(ACCOUNT, accountUuid);
		values.put(CONTACTJID, contactJid.toString());
		values.put(CREATED, created);
		values.put(STATUS, status);
		values.put(MODE, mode);
		values.put(ATTRIBUTES, attributes.toString());
		return values;
	}

	public static Conversation fromCursor(Cursor cursor) {
        Jid jid;
        try {
            jid = Jid.fromString(cursor.getString(cursor.getColumnIndex(CONTACTJID)));
        } catch (final InvalidJidException e) {
            // Borked DB..
            jid = null;
        }
        return new Conversation(cursor.getString(cursor.getColumnIndex(UUID)),
				cursor.getString(cursor.getColumnIndex(NAME)),
				cursor.getString(cursor.getColumnIndex(CONTACT)),
				cursor.getString(cursor.getColumnIndex(ACCOUNT)),
				jid,
				cursor.getLong(cursor.getColumnIndex(CREATED)),
				cursor.getInt(cursor.getColumnIndex(STATUS)),
				cursor.getInt(cursor.getColumnIndex(MODE)),
				cursor.getString(cursor.getColumnIndex(ATTRIBUTES)));
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getMode() {
		return this.mode;
	}

	public void setMode(int mode) {
		this.mode = mode;
	}

	public SessionImpl startOtrSession(XmppConnectionService service,
			String presence, boolean sendStart) {
		if (this.otrSession != null) {
			return this.otrSession;
		} else {
            final SessionID sessionId = new SessionID(this.getContactJid().toBareJid().toString(),
                    presence,
                    "xmpp");
			this.otrSession = new SessionImpl(sessionId, getAccount()
					.getOtrEngine(service));
			try {
				if (sendStart) {
					this.otrSession.startSession();
					return this.otrSession;
				}
				return this.otrSession;
			} catch (OtrException e) {
				return null;
			}
		}

	}

	public SessionImpl getOtrSession() {
		return this.otrSession;
	}

	public void resetOtrSession() {
		this.otrFingerprint = null;
		this.otrSession = null;
	}

	public void startOtrIfNeeded() {
		if (this.otrSession != null
				&& this.otrSession.getSessionStatus() != SessionStatus.ENCRYPTED) {
			try {
				this.otrSession.startSession();
			} catch (OtrException e) {
				this.resetOtrSession();
			}
		}
	}

	public boolean endOtrIfNeeded() {
		if (this.otrSession != null) {
			if (this.otrSession.getSessionStatus() == SessionStatus.ENCRYPTED) {
				try {
					this.otrSession.endSession();
					this.resetOtrSession();
					return true;
				} catch (OtrException e) {
					this.resetOtrSession();
					return false;
				}
			} else {
				this.resetOtrSession();
				return false;
			}
		} else {
			return false;
		}
	}

	public boolean hasValidOtrSession() {
		return this.otrSession != null;
	}

	public String getOtrFingerprint() {
		if (this.otrFingerprint == null) {
			try {
				if (getOtrSession() == null) {
					return "";
				}
				DSAPublicKey remotePubKey = (DSAPublicKey) getOtrSession()
						.getRemotePublicKey();
				StringBuilder builder = new StringBuilder(
						new OtrCryptoEngineImpl().getFingerprint(remotePubKey));
				builder.insert(8, " ");
				builder.insert(17, " ");
				builder.insert(26, " ");
				builder.insert(35, " ");
				this.otrFingerprint = builder.toString();
			} catch (final OtrCryptoException ignored) {

			}
		}
		return this.otrFingerprint;
	}

	public synchronized MucOptions getMucOptions() {
		if (this.mucOptions == null) {
			this.mucOptions = new MucOptions(this);
		}
		return this.mucOptions;
	}

	public void resetMucOptions() {
		this.mucOptions = null;
	}

	public void setContactJid(final Jid jid) {
		this.contactJid = jid;
	}

	public void setNextCounterpart(Jid jid) {
		this.nextCounterpart = jid;
	}

	public Jid getNextCounterpart() {
		return this.nextCounterpart;
	}

	public int getLatestEncryption() {
		int latestEncryption = this.getLatestMessage().getEncryption();
		if ((latestEncryption == Message.ENCRYPTION_DECRYPTED)
				|| (latestEncryption == Message.ENCRYPTION_DECRYPTION_FAILED)) {
			return Message.ENCRYPTION_PGP;
		} else {
			return latestEncryption;
		}
	}

	public int getNextEncryption(boolean force) {
		int next = this.getIntAttribute(ATTRIBUTE_NEXT_ENCRYPTION, -1);
		if (next == -1) {
			int latest = this.getLatestEncryption();
			if (latest == Message.ENCRYPTION_NONE) {
				if (force && getMode() == MODE_SINGLE) {
					return Message.ENCRYPTION_OTR;
				} else if (getContact().getPresences().size() == 1) {
					if (getContact().getOtrFingerprints().size() >= 1) {
						return Message.ENCRYPTION_OTR;
					} else {
						return latest;
					}
				} else {
					return latest;
				}
			} else {
				return latest;
			}
		}
		if (next == Message.ENCRYPTION_NONE && force
				&& getMode() == MODE_SINGLE) {
			return Message.ENCRYPTION_OTR;
		} else {
			return next;
		}
	}

	public void setNextEncryption(int encryption) {
		this.setAttribute(ATTRIBUTE_NEXT_ENCRYPTION, String.valueOf(encryption));
	}

	public String getNextMessage() {
		if (this.nextMessage == null) {
			return "";
		} else {
			return this.nextMessage;
		}
	}

	public void setNextMessage(String message) {
		this.nextMessage = message;
	}

	public void setSymmetricKey(byte[] key) {
		this.symmetricKey = key;
	}

	public byte[] getSymmetricKey() {
		return this.symmetricKey;
	}

	public void setBookmark(Bookmark bookmark) {
		this.bookmark = bookmark;
		this.bookmark.setConversation(this);
	}

	public void deregisterWithBookmark() {
		if (this.bookmark != null) {
			this.bookmark.setConversation(null);
		}
	}

	public Bookmark getBookmark() {
		return this.bookmark;
	}

	public boolean hasDuplicateMessage(Message message) {
		for (int i = this.getMessages().size() - 1; i >= 0; --i) {
			if (this.messages.get(i).equals(message)) {
				return true;
			}
		}
		return false;
	}

	public void setMutedTill(long value) {
		this.setAttribute(ATTRIBUTE_MUTED_TILL, String.valueOf(value));
	}

	public boolean isMuted() {
		return SystemClock.elapsedRealtime() < this.getLongAttribute(
				ATTRIBUTE_MUTED_TILL, 0);
	}

	public boolean setAttribute(String key, String value) {
		try {
			this.attributes.put(key, value);
			return true;
		} catch (JSONException e) {
			return false;
		}
	}

	public String getAttribute(String key) {
		try {
			return this.attributes.getString(key);
		} catch (JSONException e) {
			return null;
		}
	}

	public int getIntAttribute(String key, int defaultValue) {
		String value = this.getAttribute(key);
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
	}

	public long getLongAttribute(String key, long defaultValue) {
		String value = this.getAttribute(key);
		if (value == null) {
			return defaultValue;
		} else {
			try {
				return Long.parseLong(value);
			} catch (NumberFormatException e) {
				return defaultValue;
			}
		}
	}

	public void add(Message message) {
		message.setConversation(this);
		synchronized (this.messages) {
			this.messages.add(message);
		}
	}

	public void addAll(int index, List<Message> messages) {
		synchronized (this.messages) {
			this.messages.addAll(index, messages);
		}
	}
}
