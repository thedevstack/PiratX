package eu.siacs.conversations.entities;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import eu.siacs.conversations.utils.Compatibility;
import android.graphics.drawable.Drawable;
import android.text.Html;
import de.monocles.chat.BobTransfer;
import de.monocles.chat.GetThumbnailForCid;

import java.io.IOException;
import java.util.stream.Collectors;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.ui.util.QuoteHelper;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.primitives.Longs;
import de.monocles.chat.BobTransfer;
import de.monocles.chat.GetThumbnailForCid;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.net.URI;
import java.net.URISyntaxException;

import org.json.JSONException;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.crypto.axolotl.AxolotlService;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.http.URL;
import eu.siacs.conversations.services.AvatarService;
import eu.siacs.conversations.ui.util.PresenceSelector;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.MimeUtils;
import eu.siacs.conversations.utils.Patterns;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xml.Tag;
import eu.siacs.conversations.xml.XmlReader;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xml.Namespace;
import io.ipfs.cid.Cid;

public class Message extends AbstractEntity implements AvatarService.Avatarable {

    public static final String TABLENAME = "messages";

    public static final int STATUS_RECEIVED = 0;
    public static final int STATUS_UNSEND = 1;
    public static final int STATUS_SEND = 2;
    public static final int STATUS_SEND_FAILED = 3;
    public static final int STATUS_WAITING = 5;
    public static final int STATUS_OFFERED = 6;
    public static final int STATUS_SEND_RECEIVED = 7;
    public static final int STATUS_SEND_DISPLAYED = 8;

    public static final int ENCRYPTION_NONE = 0;
    public static final int ENCRYPTION_PGP = 1;
    public static final int ENCRYPTION_OTR = 2;
    public static final int ENCRYPTION_DECRYPTED = 3;
    public static final int ENCRYPTION_DECRYPTION_FAILED = 4;
    public static final int ENCRYPTION_AXOLOTL = 5;
    public static final int ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE = 6;
    public static final int ENCRYPTION_AXOLOTL_FAILED = 7;

    public static final int TYPE_TEXT = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_FILE = 2;
    public static final int TYPE_STATUS = 3;
    public static final int TYPE_PRIVATE = 4;
    public static final int TYPE_PRIVATE_FILE = 5;
    public static final int TYPE_RTP_SESSION = 6;

    public static final String CONVERSATION = "conversationUuid";
    public static final String COUNTERPART = "counterpart";
    public static final String TRUE_COUNTERPART = "trueCounterpart";
    public static final String BODY = "body";
    public static final String BODY_LANGUAGE = "bodyLanguage";
    public static final String TIME_SENT = "timeSent";
    public static final String ENCRYPTION = "encryption";
    public static final String STATUS = "status";
    public static final String TYPE = "type";
    public static final String CARBON = "carbon";
    public static final String OOB = "oob";
    public static final String EDITED = "edited";
    public static final String REMOTE_MSG_ID = "remoteMsgId";
    public static final String SERVER_MSG_ID = "serverMsgId";
    public static final String RELATIVE_FILE_PATH = "relativeFilePath";
    public static final String FINGERPRINT = "axolotl_fingerprint";
    public static final String READ = "read";
    public static final String DELETED = "deleted";
    public static final String ERROR_MESSAGE = "errorMsg";
    public static final String READ_BY_MARKERS = "readByMarkers";
    public static final String MARKABLE = "markable";
    public static final String FILE_DELETED = "file_deleted";
    public static final String ME_COMMAND = "/me";
    public static final String ERROR_MESSAGE_CANCELLED = "eu.siacs.conversations.cancelled";
    public static final String DELETED_MESSAGE_BODY = "eu.siacs.conversations.message_deleted";
    public static final String DELETED_MESSAGE_BODY_OLD = "de.pixart.messenger.message_deleted";
    public static final String RETRACT_ID = "retractId";

    public boolean markable = false;
    protected String conversationUuid;
    protected Jid counterpart;
    protected Jid trueCounterpart;
    protected String body;
    protected String encryptedBody;
    protected String subject;

    protected long timeSent;
    protected int encryption;
    protected int status;
    protected int type;
    protected boolean file_deleted = false;
    protected boolean carbon = false;
    private boolean oob = false;
    protected List<Element> payloads = new ArrayList<>();
    protected List<Edit> edits = new ArrayList<>();
    protected String relativeFilePath;
    protected boolean read = true;
    protected boolean deleted = false;
    protected String remoteMsgId = null;

    private String bodyLanguage = null;
    protected String serverMsgId = null;
    private final Conversational conversation;
    protected Transferable transferable = null;
    private Message mNextMessage = null;
    private Message mPreviousMessage = null;
    private String axolotlFingerprint = null;
    private String errorMessage = null;
    private Set<ReadByMarker> readByMarkers = new CopyOnWriteArraySet<>();
    private String retractId = null;
    protected int resendCount = 0;

    private Boolean isGeoUri = null;
    private Boolean isXmppUri = null;
    private Boolean isWebUri = null;
    private String WebUri = null;
    private Boolean isEmojisOnly = null;
    private Boolean treatAsDownloadable = null;
    private FileParams fileParams = null;
    private List<MucOptions.User> counterparts;
    private WeakReference<MucOptions.User> user;


    protected Message(Conversational conversation) {
        this.conversation = conversation;
    }

    public Message(Conversational conversation, String body, int encryption) {
        this(conversation, body, encryption, STATUS_UNSEND);
    }

    public Message(Conversational conversation, String body, int encryption, int status) {
        this(conversation, java.util.UUID.randomUUID().toString(),
                conversation.getUuid(),
                conversation.getJid() == null ? null : conversation.getJid().asBareJid(),
                null,
                body,
                System.currentTimeMillis(),
                encryption,
                status,
                TYPE_TEXT,
                false,
                null,
                null,
                null,
                null,
                true,
                false,
                null,
                false,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    public Message(Conversation conversation, int status, int type, final String remoteMsgId) {
        this(conversation, java.util.UUID.randomUUID().toString(),
                conversation.getUuid(),
                conversation.getJid() == null ? null : conversation.getJid().asBareJid(),
                null,
                null,
                System.currentTimeMillis(),
                Message.ENCRYPTION_NONE,
                status,
                type,
                false,
                remoteMsgId,
                null,
                null,
                null,
                true,
                false,
                null,
                false,
                null,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    protected Message(final Conversational conversation, final String uuid, final String conversationUUid, final Jid counterpart,
                      final Jid trueCounterpart, final String body, final long timeSent,
                      final int encryption, final int status, final int type, final boolean carbon,
                      final String remoteMsgId, final String relativeFilePath,
                      final String serverMsgId, final String fingerprint, final boolean read, final boolean deleted,
                      final String edited, final boolean oob, final String errorMessage, final Set<ReadByMarker> readByMarkers,
                      final boolean markable, final boolean file_deleted, final String bodyLanguage, final String retractId, final String subject, final String fileParams, final List<Element> payloads) {
        this.conversation = conversation;
        this.uuid = uuid;
        this.conversationUuid = conversationUUid;
        this.counterpart = counterpart;
        this.trueCounterpart = trueCounterpart;
        this.body = body == null ? "" : body;
        this.timeSent = timeSent;
        this.encryption = encryption;
        this.status = status;
        this.type = type;
        this.carbon = carbon;
        this.remoteMsgId = remoteMsgId;
        this.relativeFilePath = relativeFilePath;
        this.serverMsgId = serverMsgId;
        this.axolotlFingerprint = fingerprint;
        this.read = read;
        this.deleted = deleted;
        this.edits = Edit.fromJson(edited);
        this.oob = oob;
        this.errorMessage = errorMessage;
        this.readByMarkers = readByMarkers == null ? new CopyOnWriteArraySet<>() : readByMarkers;
        this.markable = markable;
        this.file_deleted = file_deleted;
        this.bodyLanguage = bodyLanguage;
        this.retractId = retractId;
        this.subject = subject;
        if (fileParams != null) this.fileParams = new FileParams(fileParams);
        if (payloads != null) this.payloads = payloads;
    }

    public static Message fromCursor(Cursor cursor, Conversation conversation) throws IOException {
        String payloadsStr = cursor.getString(cursor.getColumnIndex("payloads"));
        List<Element> payloads = new ArrayList<>();
        if (payloadsStr != null) {
            final XmlReader xmlReader = new XmlReader();
            xmlReader.setInputStream(ByteSource.wrap(payloadsStr.getBytes()).openStream());
            Tag tag;
            while ((tag = xmlReader.readTag()) != null) {
                payloads.add(xmlReader.readElement(tag));
            }
        }

        return new Message(conversation,
                cursor.getString(cursor.getColumnIndex(UUID)),
                cursor.getString(cursor.getColumnIndex(CONVERSATION)),
                fromString(cursor.getString(cursor.getColumnIndex(COUNTERPART))),
                fromString(cursor.getString(cursor.getColumnIndex(TRUE_COUNTERPART))),
                cursor.getString(cursor.getColumnIndex(BODY)),
                cursor.getLong(cursor.getColumnIndex(TIME_SENT)),
                cursor.getInt(cursor.getColumnIndex(ENCRYPTION)),
                cursor.getInt(cursor.getColumnIndex(STATUS)),
                cursor.getInt(cursor.getColumnIndex(TYPE)),
                cursor.getInt(cursor.getColumnIndex(CARBON)) > 0,
                cursor.getString(cursor.getColumnIndex(REMOTE_MSG_ID)),
                cursor.getString(cursor.getColumnIndex(RELATIVE_FILE_PATH)),
                cursor.getString(cursor.getColumnIndex(SERVER_MSG_ID)),
                cursor.getString(cursor.getColumnIndex(FINGERPRINT)),
                cursor.getInt(cursor.getColumnIndex(READ)) > 0,
                cursor.getInt(cursor.getColumnIndex(DELETED)) > 0,
                cursor.getString(cursor.getColumnIndex(EDITED)),
                cursor.getInt(cursor.getColumnIndex(OOB)) > 0,
                cursor.getString(cursor.getColumnIndex(ERROR_MESSAGE)),
                ReadByMarker.fromJsonString(cursor.getString(cursor.getColumnIndex(READ_BY_MARKERS))),
                cursor.getInt(cursor.getColumnIndex(MARKABLE)) > 0,
                cursor.getInt(cursor.getColumnIndex(FILE_DELETED)) > 0,
                cursor.getString(cursor.getColumnIndex(BODY_LANGUAGE)),
                cursor.getString(cursor.getColumnIndex(RETRACT_ID)),
                cursor.getString(cursor.getColumnIndex("subject")),
                cursor.getString(cursor.getColumnIndex("fileParams")),
                payloads
        );
    }

    private static Jid fromString(String value) {
        try {
            if (value != null) {
                return Jid.of(value);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    public static Message createStatusMessage(Conversation conversation, String body) {
        final Message message = new Message(conversation);
        message.setType(Message.TYPE_STATUS);
        message.setStatus(Message.STATUS_RECEIVED);
        message.body = body;
        return message;
    }

    public static Message createLoadMoreMessage(Conversation conversation) {
        final Message message = new Message(conversation);
        message.setType(Message.TYPE_STATUS);
        message.body = "LOAD_MORE";
        return message;
    }
    public ContentValues getmonoclesContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put("subject", subject);
        values.put("fileParams", fileParams == null ? null : fileParams.toString());
        values.put("payloads", payloads.size() < 1 ? null : payloads.stream().map(Object::toString).collect(Collectors.joining()));
        return values;
    }

    @Override
    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        values.put(UUID, uuid);
        values.put(CONVERSATION, conversationUuid);
        if (counterpart == null) {
            values.putNull(COUNTERPART);
        } else {
            values.put(COUNTERPART, counterpart.toString());
        }
        if (trueCounterpart == null) {
            values.putNull(TRUE_COUNTERPART);
        } else {
            values.put(TRUE_COUNTERPART, trueCounterpart.toString());
        }
        values.put(BODY, body.length() > Config.MAX_STORAGE_MESSAGE_CHARS ? body.substring(0, Config.MAX_STORAGE_MESSAGE_CHARS) : body);
        values.put(TIME_SENT, timeSent);
        values.put(ENCRYPTION, encryption);
        values.put(STATUS, status);
        values.put(TYPE, type);
        values.put(CARBON, carbon ? 1 : 0);
        values.put(REMOTE_MSG_ID, remoteMsgId);
        values.put(RELATIVE_FILE_PATH, relativeFilePath);
        values.put(SERVER_MSG_ID, serverMsgId);
        values.put(FINGERPRINT, axolotlFingerprint);
        values.put(READ, read ? 1 : 0);
        values.put(DELETED, deleted ? 1 : 0);
        try {
            values.put(EDITED, Edit.toJson(edits, retractId != null || deleted));
        } catch (JSONException e) {
            Log.e(Config.LOGTAG, "error persisting json for edits", e);
        }
        values.put(OOB, oob ? 1 : 0);
        values.put(ERROR_MESSAGE, errorMessage);
        values.put(READ_BY_MARKERS, ReadByMarker.toJson(readByMarkers).toString());
        values.put(MARKABLE, markable ? 1 : 0);
        values.put(FILE_DELETED, file_deleted ? 1 : 0);
        values.put(BODY_LANGUAGE, bodyLanguage);
        values.put(RETRACT_ID, retractId);
        return values;
    }
    public String replyId() {
        return conversation.getMode() == Conversation.MODE_MULTI ? getServerMsgId() : getRemoteMsgId();
    }
    public Message reply() {
        Message m = new Message(conversation, QuoteHelper.quote(MessageUtils.prepareQuote(this)) + "\n", ENCRYPTION_NONE);
        m.setThread(getThread());
        m.addPayload(
                new Element("reply", "urn:xmpp:reply:0")
                        .setAttribute("to", getCounterpart())
                        .setAttribute("id", replyId())
        );
        final Element fallback = new Element("fallback", "urn:xmpp:fallback:0").setAttribute("for", "urn:xmpp:reply:0");
        fallback.addChild("body", "urn:xmpp:fallback:0")
                .setAttribute("start", "0")
                .setAttribute("end", "" + m.body.codePointCount(0, m.body.length()));
        m.addPayload(fallback);
        return m;
    }

    public Message react(String emoji) {
        Set<String> emojis = new HashSet<>();
        if (conversation instanceof Conversation) emojis = ((Conversation) conversation).findReactionsTo(replyId(), null);
        emojis.add(emoji);
        final Message m = reply();
        m.appendBody(emoji);
        final Element fallback = new Element("fallback", "urn:xmpp:fallback:0").setAttribute("for", "urn:xmpp:reactions:0");
        fallback.addChild("body", "urn:xmpp:fallback:0");
        m.addPayload(fallback);
        final Element reactions = new Element("reactions", "urn:xmpp:reactions:0").setAttribute("id", replyId());
        for (String oneEmoji : emojis) {
            reactions.addChild("reaction", "urn:xmpp:reactions:0").setContent(oneEmoji);
        }
        m.addPayload(reactions);
        return m;
    }

    public void setReactions(Element reactions) {
        if (this.payloads != null) {
            this.payloads.remove(getReactions());
        }
        addPayload(reactions);
    }

    public Element getThread() {
        if (this.payloads == null) return null;

        for (Element el : this.payloads) {
            if (el.getName().equals("thread") && el.getNamespace().equals("jabber:client")) {
                return el;
            }
        }

        return null;
    }
    public synchronized void clearFallbacks() {
        this.payloads.removeAll(getFallbacks());
    }
    public List<Element> getFallbacks() {
        List<Element> fallbacks = new ArrayList<>();

        if (this.payloads == null) return fallbacks;

        for (Element el : this.payloads) {
            if (el.getName().equals("fallback") && el.getNamespace().equals("urn:xmpp:fallback:0")) {
                final String fallbackFor = el.getAttribute("for");
                if (fallbackFor == null) continue;
                if (fallbackFor.equals("http://jabber.org/protocol/address") || fallbackFor.equals(Namespace.OOB)) {
                    fallbacks.add(el);
                }
            }
        }

        return fallbacks;
    }

    public String getQuoteableBody() {
        return this.body;
    }
    public void setThread(Element thread) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            payloads.removeIf(el -> el.getName().equals("thread") && el.getNamespace().equals("jabber:client"));
        }
        addPayload(thread);
    }
    public Element getReactions() {
        if (this.payloads == null) return null;

        for (Element el : this.payloads) {
            if (el.getName().equals("reactions") && el.getNamespace().equals("urn:xmpp:reactions:0")) {
                return el;
            }
        }

        return null;
    }

    public synchronized void appendBody(String append) {
        this.body += append;
        this.isGeoUri = null;
        this.isEmojisOnly = null;
        this.treatAsDownloadable = null;
    }
    public String getConversationUuid() {
        return conversationUuid;
    }

    public Conversational getConversation() {
        return this.conversation;
    }

    public Jid getCounterpart() {
        return counterpart;
    }

    public void setCounterpart(final Jid counterpart) {
        this.counterpart = counterpart;
    }

    public Contact getContact() {
        if (this.conversation.getMode() == Conversation.MODE_SINGLE) {
            return this.conversation.getContact();
        } else {
            if (this.trueCounterpart == null) {
                return null;
            } else {
                return this.conversation.getAccount().getRoster()
                        .getContactFromContactList(this.trueCounterpart);
            }
        }
    }

    public String getBody() {
        if (getOob() != null) {
            return body.replace(getOob().toString(), "");
        } else {
            return body;
        }
    }

    public synchronized void setBody(String body) {
        if (body == null) {
            throw new Error("You should not set the message body to null");
        }
        this.body = body;
        this.isGeoUri = null;
        this.isXmppUri = null;
        this.isWebUri = null;
        this.isEmojisOnly = null;
        this.treatAsDownloadable = null;
    }
    public String getSubject() {
        return subject;
    }

    public synchronized void setSubject(String subject) {
        this.subject = subject;
    }

    public void setMucUser(MucOptions.User user) {
        this.user = new WeakReference<>(user);
    }

    public boolean sameMucUser(Message otherMessage) {
        final MucOptions.User thisUser = this.user == null ? null : this.user.get();
        final MucOptions.User otherUser = otherMessage.user == null ? null : otherMessage.user.get();
        return thisUser != null && thisUser == otherUser;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean setErrorMessage(String message) {
        boolean changed = (message != null && !message.equals(errorMessage))
                || (message == null && errorMessage != null);
        this.errorMessage = message;
        return changed;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public int getEncryption() {
        return encryption;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getRelativeFilePath() {
        return this.relativeFilePath;
    }

    public void setRelativeFilePath(String path) {
        this.relativeFilePath = path;
    }

    public String getRemoteMsgId() {
        return this.remoteMsgId;
    }

    public void setRemoteMsgId(String id) {
        this.remoteMsgId = id;
    }

    public String getServerMsgId() {
        return this.serverMsgId;
    }

    public void setServerMsgId(String id) {
        this.serverMsgId = id;
    }

    public boolean isRead() {
        return this.read;
    }

    public boolean isMessageDeleted() {
        return this.deleted;
    }

    public void setMessageDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public boolean isFileDeleted() {
        return this.file_deleted;
    }

    public void setFileDeleted(boolean file_deleted) {
        this.file_deleted = file_deleted;
    }
    public Element getModerated() {
        if (this.payloads == null) return null;

        for (Element el : this.payloads) {
            if (el.getName().equals("moderated") && el.getNamespace().equals("urn:xmpp:message-moderate:0")) {
                return el;
            }
        }

        return null;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public void markRead() {
        this.read = true;
    }

    public void markUnread() {
        this.read = false;
    }

    public void setTime(long time) {
        this.timeSent = time;
    }

    public String getEncryptedBody() {
        return this.encryptedBody;
    }

    public void setEncryptedBody(String body) {
        this.encryptedBody = body;
    }

    public int getType() {
        return this.type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public boolean isCarbon() {
        return carbon;
    }

    public void setCarbon(boolean carbon) {
        this.carbon = carbon;
    }

    public void putEdited(String edited, String serverMsgId, String body, long timeSent) {
        final Edit edit = new Edit(edited, serverMsgId, body, timeSent);
        if (this.edits.size() < 128 && !this.edits.contains(edit)) {
            this.edits.add(edit);
        }
    }

    boolean remoteMsgIdMatchInEdit(String id) {
        for (Edit edit : this.edits) {
            if (id.equals(edit.getEditedId())) {
                return true;
            }
        }
        return false;
    }

    public String getBodyLanguage() {
        return this.bodyLanguage;
    }

    public void setBodyLanguage(String language) {
        this.bodyLanguage = language;
    }

    public boolean edited() {
        return this.edits.size() > 0;
    }

    public void setTrueCounterpart(Jid trueCounterpart) {
        this.trueCounterpart = trueCounterpart;
    }

    public Jid getTrueCounterpart() {
        return this.trueCounterpart;
    }

    public Transferable getTransferable() {
        return this.transferable;
    }

    public String getRetractId() {
        return this.retractId;
    }

    public void setRetractId(String id) {
        this.retractId = id;
    }

    public synchronized void setTransferable(Transferable transferable) {
        this.transferable = transferable;
    }

    public boolean addReadByMarker(ReadByMarker readByMarker) {
        if (readByMarker.getRealJid() != null) {
            if (readByMarker.getRealJid().asBareJid().equals(trueCounterpart)) {
                Log.d(Config.LOGTAG, "trying to add read marker by " + readByMarker.getRealJid() + " to " + body);
                return false;
            }
        } else if (readByMarker.getFullJid() != null) {
            if (readByMarker.getFullJid().equals(counterpart)) {
                Log.d(Config.LOGTAG, "trying to add read marker by " + readByMarker.getFullJid() + " to " + body);
                return false;
            }
        }
        if (this.readByMarkers.add(readByMarker)) {
            if (readByMarker.getRealJid() != null && readByMarker.getFullJid() != null) {
                Iterator<ReadByMarker> iterator = this.readByMarkers.iterator();
                while (iterator.hasNext()) {
                    ReadByMarker marker = iterator.next();
                    if (marker.getRealJid() == null && readByMarker.getFullJid().equals(marker.getFullJid())) {
                        iterator.remove();
                    }
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public Set<ReadByMarker> getReadByMarkers() {
        return ImmutableSet.copyOf(this.readByMarkers);
    }

    boolean similar(Message message) {
        if (!isPrivateMessage() && this.serverMsgId != null && message.getServerMsgId() != null) {
            return this.serverMsgId.equals(message.getServerMsgId()) || Edit.wasPreviouslyEditedServerMsgId(edits, message.getServerMsgId());
        } else if (Edit.wasPreviouslyEditedServerMsgId(edits, message.getServerMsgId())) {
            return true;
        } else if (this.body == null || this.counterpart == null) {
            return false;
        } else {
            String body, otherBody;
            if (this.hasFileOnRemoteHost()) {
                body = getFileParams().url;
                otherBody = message.body == null ? null : message.body.trim();
            } else {
                body = this.body;
                otherBody = message.body;
            }
            final boolean matchingCounterpart = this.counterpart.equals(message.getCounterpart());
            if (message.getRemoteMsgId() != null) {
                final boolean hasUuid = CryptoHelper.UUID_PATTERN.matcher(message.getRemoteMsgId()).matches();
                if (hasUuid && matchingCounterpart && Edit.wasPreviouslyEditedRemoteMsgId(edits, message.getRemoteMsgId())) {
                    return true;
                }
                return (message.getRemoteMsgId().equals(this.remoteMsgId) || message.getRemoteMsgId().equals(this.uuid))
                        && matchingCounterpart
                        && (body.equals(otherBody) || (message.getEncryption() == Message.ENCRYPTION_PGP && hasUuid));
            } else {
                return this.remoteMsgId == null
                        && matchingCounterpart
                        && body.equals(otherBody)
                        && Math.abs(this.getTimeSent() - message.getTimeSent()) < Config.MESSAGE_MERGE_WINDOW * 1000;
            }
        }
    }

    public Message next() {
        if (this.conversation instanceof Conversation) {
            final Conversation conversation = (Conversation) this.conversation;
            synchronized (conversation.messages) {
                if (this.mNextMessage == null) {
                    int index = conversation.messages.indexOf(this);
                    if (index < 0 || index >= conversation.messages.size() - 1) {
                        this.mNextMessage = null;
                    } else {
                        this.mNextMessage = conversation.messages.get(index + 1);
                    }
                }
                return this.mNextMessage;
            }
        } else {
            throw new AssertionError("Calling next should be disabled for stubs");
        }
    }

    public Message prev() {
        if (this.conversation instanceof Conversation) {
            final Conversation conversation = (Conversation) this.conversation;
            synchronized (conversation.messages) {
                if (this.mPreviousMessage == null) {
                    int index = conversation.messages.indexOf(this);
                    if (index <= 0 || index > conversation.messages.size()) {
                        this.mPreviousMessage = null;
                    } else {
                        this.mPreviousMessage = conversation.messages.get(index - 1);
                    }
                }
            }
            return this.mPreviousMessage;
        } else {
            throw new AssertionError("Calling prev should be disabled for stubs");
        }
    }

    public boolean isLastCorrectableMessage() {
        Message next = next();
        while (next != null) {
            if (next.isEditable()) {
                return false;
            }
            next = next.next();
        }
        return isEditable();
    }

    public boolean isEditable() {
        return status != STATUS_RECEIVED && type != Message.TYPE_RTP_SESSION;
    }

    public boolean mergeable(final Message message) {
        try {
            boolean mergeAllowed = this.conversation.getAccount().getXmppConnection().getXmppConnectionService().allowMergeMessages();
            return mergeAllowed && (message != null &&
                    (message.getType() == Message.TYPE_TEXT &&
                            this.getTransferable() == null &&
                            message.getTransferable() == null &&
                            message.getEncryption() != Message.ENCRYPTION_PGP &&
                            message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED &&
                            this.getType() == message.getType() &&
                            this.getSubject() != null &&
                            //this.getStatus() == message.getStatus() &&
                            isStatusMergeable(this.getStatus(), message.getStatus()) &&
                            this.getEncryption() == message.getEncryption() &&
                            this.getCounterpart() != null &&
                            this.getCounterpart().equals(message.getCounterpart()) &&
                            this.edited() == message.edited() &&
                            !this.isMessageDeleted() == !message.isMessageDeleted() &&
                            (message.getTimeSent() - this.getTimeSent()) <= (Config.MESSAGE_MERGE_WINDOW * 1000) &&
                            this.getBody().length() + message.getBody().length() <= Config.MAX_DISPLAY_MESSAGE_CHARS &&
                            !message.isGeoUri() &&
                            !this.isGeoUri() &&
                            !message.isWebUri() &&
                            !this.isWebUri() &&
                            !message.isOOb() &&
                            !this.isOOb() &&
                            !message.treatAsDownloadable() &&
                            !this.treatAsDownloadable() &&
                            !message.hasMeCommand() &&
                            !this.hasMeCommand() &&
                            !message.bodyIsOnlyEmojis() &&
                            !this.bodyIsOnlyEmojis() &&
                            !message.isXmppUri() &&
                            !this.isXmppUri() &&
                            !message.hasDeletedBody() &&
                            !this.hasDeletedBody() &&
                            ((this.axolotlFingerprint == null && message.axolotlFingerprint == null) || this.axolotlFingerprint.equals(message.getFingerprint())) &&
                            UIHelper.sameDay(message.getTimeSent(), this.getTimeSent()) &&
                            this.getReadByMarkers().equals(message.getReadByMarkers()) &&
                            !this.conversation.getJid().asBareJid().equals(Config.BUG_REPORTS)
                    ));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private static boolean isStatusMergeable(int a, int b) {
        return a == b || (
                (a == Message.STATUS_SEND_RECEIVED && b == Message.STATUS_UNSEND)
                        || (a == Message.STATUS_SEND_RECEIVED && b == Message.STATUS_SEND)
                        || (a == Message.STATUS_SEND_RECEIVED && b == Message.STATUS_WAITING)
                        || (a == Message.STATUS_SEND && b == Message.STATUS_UNSEND)
                        || (a == Message.STATUS_SEND && b == Message.STATUS_WAITING)
        );
    }

    public void setCounterparts(List<MucOptions.User> counterparts) {
        this.counterparts = counterparts;
    }

    public List<MucOptions.User> getCounterparts() {
        return this.counterparts;
    }

    @Override
    public int getAvatarBackgroundColor() {
        if (type == Message.TYPE_STATUS && getCounterparts() != null && getCounterparts().size() > 1) {
            return Color.TRANSPARENT;
        } else {
            return UIHelper.getColorForName(UIHelper.getMessageDisplayName(this));
        }
    }

    @Override
    public String getAvatarName() {
        return UIHelper.getMessageDisplayName(this);
    }

    public boolean isOOb() {
        return oob || getFileParams().url != null;
    }

    public static class MergeSeparator {
    }
    public SpannableStringBuilder getSpannableBody(GetThumbnailForCid thumbnailer, Drawable fallbackImg) {
        final Element html = getHtml();
        if (html == null || Build.VERSION.SDK_INT < 24) {
            return new SpannableStringBuilder(MessageUtils.filterLtrRtl(getBody()).trim());
        } else {
            SpannableStringBuilder spannable = new SpannableStringBuilder(Html.fromHtml(
                    MessageUtils.filterLtrRtl(html.toString()).trim(),
                    Html.FROM_HTML_MODE_COMPACT,
                    (source) -> {
                        try {
                            if (thumbnailer == null) return fallbackImg;
                            Cid cid = BobTransfer.cid(new URI(source));
                            if (cid == null) return fallbackImg;
                            Drawable thumbnail = thumbnailer.getThumbnail(cid);
                            if (thumbnail == null) return fallbackImg;
                            return thumbnail;
                        } catch (final URISyntaxException e) {
                            return fallbackImg;
                        }
                    },
                    (opening, tag, output, xmlReader) -> {}
            ));

            // https://stackoverflow.com/a/10187511/8611
            int i = spannable.length();
            while(--i >= 0 && Character.isWhitespace(spannable.charAt(i))) { }
            return (SpannableStringBuilder) spannable.subSequence(0, i+1);
        }
    }


    public Element getHtml() {
        if (this.payloads == null) return null;

        for (Element el : this.payloads) {
            if (el.getName().equals("html") && el.getNamespace().equals("http://jabber.org/protocol/xhtml-im")) {
                return el.getChildren().get(0);
            }
        }

        return null;
    }

    public SpannableStringBuilder getMergedBody() {
        return getMergedBody(null, null);
    }

    public SpannableStringBuilder getMergedBody(GetThumbnailForCid thumbnailer, Drawable fallbackImg) {
        SpannableStringBuilder body = getSpannableBody(thumbnailer, fallbackImg);
        Message current = this;
        while (current.mergeable(current.next())) {
            current = current.next();
            if (current == null) {
                break;
            }
            body.append("\n\n");
            body.setSpan(new MergeSeparator(), body.length() - 2, body.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.append(current.getSpannableBody(thumbnailer, fallbackImg));
        }
        return body;
    }

    public boolean hasMeCommand() {
        return this.body.trim().startsWith(ME_COMMAND);
    }

    public boolean hasDeletedBody() {
        return this.body.trim().equals(DELETED_MESSAGE_BODY) || this.body.trim().equals(DELETED_MESSAGE_BODY_OLD);
    }

    public int getMergedStatus() {
        int status = this.status;
        Message current = this;
        while (current.mergeable(current.next())) {
            current = current.next();
            if (current == null) {
                break;
            }
            status = current.status;
        }
        return status;
    }

    public long getMergedTimeSent() {
        long time = this.timeSent;
        Message current = this;
        while (current.mergeable(current.next())) {
            current = current.next();
            if (current == null) {
                break;
            }
            time = current.timeSent;
        }
        return time;
    }

    public boolean wasMergedIntoPrevious() {
        Message prev = this.prev();
        return prev != null && prev.mergeable(this);
    }

    public boolean trusted() {
        Contact contact = this.getContact();
        return status > STATUS_RECEIVED || (contact != null && (contact.showInContactList() || contact.isSelf()));
    }

    public boolean fixCounterpart() {
        final Presences presences = conversation.getContact().getPresences();
        if (counterpart != null && presences.has(Strings.nullToEmpty(counterpart.getResource()))) {
            return true;
        } else if (presences.size() >= 1) {
            counterpart = PresenceSelector.getNextCounterpart(getContact(),presences.toResourceArray()[0]);
            return true;
        } else {
            counterpart = null;
            return false;
        }
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getEditedId() {
        if (edits.size() > 0) {
            return edits.get(edits.size() - 1).getEditedId();
        } else {
            throw new IllegalStateException("Attempting to store unedited message");
        }
    }

    public List<Edit> getEditedList() {
        return edits;
    }

    public String getEditedIdWireFormat() {
        if (edits.size() > 0) {
            return edits.get(Config.USE_LMC_VERSION_1_1 ? 0 : edits.size() - 1).getEditedId();
        } else {
            throw new IllegalStateException("Attempting to store unedited message");
        }
    }

    public URI getOob() {
        final String url = getFileParams().url;
        try {
            return url == null ? null : new URI(url);
        } catch (final URISyntaxException e) {
            return null;
        }
    }

    public void clearPayloads() {
        this.payloads.clear();
    }

    public void addPayload(Element el) {
        if (el == null) return;

        this.payloads.add(el);
    }

    public List<Element> getCommands() {
        if (this.payloads == null) return null;

        for (Element el : this.payloads) {
            if (el.getName().equals("query") && el.getNamespace().equals("http://jabber.org/protocol/disco#items") && el.getAttribute("node").equals("http://jabber.org/protocol/commands")) {
                return el.getChildren();
            }
        }

        return null;
    }



    public List<Element> getPayloads() {
        return new ArrayList<>(this.payloads);
    }

    public String getMimeType() {
        String extension;
        if (relativeFilePath != null) {
            extension = MimeUtils.extractRelevantExtension(relativeFilePath);
        } else {
            final String url = URL.tryParse(getOob() == null ? body.split("\n")[0] : getOob().toString());
            if (url == null) {
                return null;
            }
            extension = MimeUtils.extractRelevantExtension(url);
        }
        return MimeUtils.guessMimeTypeFromExtension(extension);
    }

    public synchronized boolean treatAsDownloadable() {
        if (treatAsDownloadable == null) {
            treatAsDownloadable = MessageUtils.treatAsDownloadable(this.body, isOOb());
        }
        return treatAsDownloadable;
    }

    public synchronized boolean bodyIsOnlyEmojis() {
        if (isEmojisOnly == null) {
            isEmojisOnly = Emoticons.isOnlyEmoji(getBody().replaceAll("\\s", ""));
        }
        return isEmojisOnly;
    }

    public synchronized boolean isXmppUri() {
        if (isXmppUri == null) {
            isXmppUri = XmppUri.XMPP_URI.matcher(body).matches();
        }
        return isXmppUri;
    }

    public synchronized boolean isGeoUri() {
        if (isGeoUri == null) {
            isGeoUri = GeoHelper.GEO_URI.matcher(body).matches();
        }
        return isGeoUri;
    }

    public synchronized boolean isWebUri() {
        if (isWebUri == null) {
            isWebUri = Patterns.WEB_URL.matcher(body).matches();
        }
        return isWebUri;
    }

    public synchronized String getWebUri() {
        final Pattern urlPattern = Pattern.compile(
                "(?:(?:https?):\\/\\/|www\\.)(?:\\([-A-Z0-9+&@#\\/%=~_|$?!:,.]*\\)|[-A-Z0-9+&@#\\/%=~_|$?!:,.])*(?:\\([-A-Z0-9+&@#\\/%=~_|$?!:,.]*\\)|[A-Z0-9+&@#\\/%=~_|$])",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        Matcher m = urlPattern.matcher(body);
        while (m.find()) {
            if (WebUri == null) {
                WebUri = m.group(0);
                Log.d(Config.LOGTAG, "Weburi Message: " + WebUri);
                return WebUri;
            }
        }
        return WebUri;
    }

    public synchronized void resetFileParams() {
        if (fileParams != null && this.fileParams != null && this.fileParams.sims != null && fileParams.sims == null) {
            fileParams.sims = this.fileParams.sims;
        }
        this.fileParams = fileParams;
    }
    public synchronized void setFileParams(FileParams fileParams) {
        this.fileParams = fileParams;
    }

    public synchronized FileParams getFileParams() {
        if (fileParams == null) {
            fileParams = new FileParams(this.body);
            if (this.transferable != null) {
                fileParams.size = this.transferable.getFileSize();
            }
        }
        return fileParams;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String parseString(String value) {
        try {
            return value;
        } catch (Exception e) {
            return "";
        }
    }

    public void untie() {
        this.mNextMessage = null;
        this.mPreviousMessage = null;
    }

    public boolean isPrivateMessage() {
        return type == TYPE_PRIVATE || type == TYPE_PRIVATE_FILE;
    }

    public boolean isFileOrImage() {
        return type == TYPE_FILE || type == TYPE_IMAGE || type == TYPE_PRIVATE_FILE;
    }


    public boolean isTypeText() {
        return type == TYPE_TEXT || type == TYPE_PRIVATE;
    }

    public boolean hasFileOnRemoteHost() {
        return isFileOrImage() && getFileParams().url != null;
    }

    public boolean needsUploading() {
        return isFileOrImage() && getFileParams().url == null;
    }

    public boolean fileIsTransferring() {
        return transferable.getStatus() == Transferable.STATUS_DOWNLOADING || transferable.getStatus() == Transferable.STATUS_UPLOADING || transferable.getStatus() == Transferable.STATUS_WAITING;
    }

    public static class FileParams {
        public String url;
        public Long size = null;
        public int width = 0;
        public int height = 0;
        public int runtime = 0;
        public String subject = "";
        public Element sims = null;
        public FileParams() { }

        public FileParams(Element el) {
            if (el.getName().equals("x") && el.getNamespace().equals(Namespace.OOB)) {
                this.url = el.findChildContent("url", Namespace.OOB);
            }
            if (el.getName().equals("reference") && el.getNamespace().equals("urn:xmpp:reference:0")) {
                final String refUri = el.getAttribute("uri");
                if (refUri != null) url = refUri;
                final Element mediaSharing = el.findChild("media-sharing", "urn:xmpp:sims:1");
                if (mediaSharing != null) {
                    Element file = mediaSharing.findChild("file", "urn:xmpp:jingle:apps:file-transfer:5");
                    if (file == null) file = mediaSharing.findChild("file", "urn:xmpp:jingle:apps:file-transfer:4");
                    if (file == null) file = mediaSharing.findChild("file", "urn:xmpp:jingle:apps:file-transfer:3");
                    if (file != null) {
                        String sizeS = file.findChildContent("size", "urn:xmpp:jingle:apps:file-transfer:5");
                        if (sizeS == null) sizeS = file.findChildContent("size", "urn:xmpp:jingle:apps:file-transfer:4");
                        if (sizeS == null) sizeS = file.findChildContent("size", "urn:xmpp:jingle:apps:file-transfer:3");
                        if (sizeS != null) size = new Long(sizeS);
                    }

                    final Element sources = mediaSharing.findChild("sources", "urn:xmpp:sims:1");
                    if (sources != null) {
                        final Element ref = sources.findChild("reference", "urn:xmpp:reference:0");
                        if (ref != null) url = ref.getAttribute("uri");
                    }
                }
            }
        }


        public FileParams(String ser) {
            final String[] parts = ser == null ? new String[0] : ser.split("\\|");
            switch (parts.length) {
                case 1:
                    try {
                        this.size = Long.parseLong(parts[0]);
                    } catch (final NumberFormatException e) {
                        this.url = URL.tryParse(parts[0]);
                    }
                    break;
                case 5:
                    this.runtime = parseInt(parts[4]);
                case 4:
                    this.width = parseInt(parts[2]);
                    this.height = parseInt(parts[3]);
                case 2:
                    this.url = URL.tryParse(parts[0]);
                    this.size = Longs.tryParse(parts[1]);
                    break;
                case 3:
                    this.size = Longs.tryParse(parts[0]);
                    this.width = parseInt(parts[1]);
                    this.height = parseInt(parts[2]);
                    break;
            }
        }
        public long getSize() {
            return size == null ? 0 : size;
        }

        public String toString() {
            final StringBuilder builder = new StringBuilder();
            if (url != null) builder.append(url);
            if (size != null) builder.append('|').append(size.toString());
            if (width > 0 || height > 0 || runtime > 0) builder.append('|').append(width);
            if (height > 0 || runtime > 0) builder.append('|').append(height);
            if (runtime > 0) builder.append('|').append(runtime);
            return builder.toString();
        }
    }

    public void setFingerprint(String fingerprint) {
        this.axolotlFingerprint = fingerprint;
    }

    public String getFingerprint() {
        return axolotlFingerprint;
    }

    public boolean isTrusted() {
        final AxolotlService axolotlService = conversation.getAccount().getAxolotlService();
        final FingerprintStatus s = axolotlService != null ? axolotlService.getFingerprintTrust(axolotlFingerprint) : null;
        return s != null && s.isTrusted();
    }

    private int getPreviousEncryption() {
        for (Message iterator = this.prev(); iterator != null; iterator = iterator.prev()) {
            if (iterator.isCarbon() || iterator.getStatus() == STATUS_RECEIVED) {
                continue;
            }
            return iterator.getEncryption();
        }
        return ENCRYPTION_NONE;
    }

    private int getNextEncryption() {
        if (this.conversation instanceof Conversation) {
            Conversation conversation = (Conversation) this.conversation;
            for (Message iterator = this.next(); iterator != null; iterator = iterator.next()) {
                if (iterator.isCarbon() || iterator.getStatus() == STATUS_RECEIVED) {
                    continue;
                }
                return iterator.getEncryption();
            }
            return conversation.getNextEncryption();
        } else {
            throw new AssertionError("This should never be called since isInValidSession should be disabled for stubs");
        }
    }

    public boolean isValidInSession() {
        int pastEncryption = getCleanedEncryption(this.getPreviousEncryption());
        int futureEncryption = getCleanedEncryption(this.getNextEncryption());

        boolean inUnencryptedSession = pastEncryption == ENCRYPTION_NONE
                || futureEncryption == ENCRYPTION_NONE
                || pastEncryption != futureEncryption;

        return inUnencryptedSession || getCleanedEncryption(this.getEncryption()) == pastEncryption;
    }

    private static int getCleanedEncryption(int encryption) {
        if (encryption == ENCRYPTION_DECRYPTED || encryption == ENCRYPTION_DECRYPTION_FAILED) {
            return ENCRYPTION_PGP;
        }
        if (encryption == ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE || encryption == ENCRYPTION_AXOLOTL_FAILED) {
            return ENCRYPTION_AXOLOTL;
        }
        return encryption;
    }

    public static boolean configurePrivateMessage(final Message message) {
        return configurePrivateMessage(message, false);
    }

    public static boolean configurePrivateFileMessage(final Message message) {
        return configurePrivateMessage(message, true);
    }

    private static boolean configurePrivateMessage(final Message message, final boolean isFile) {
        final Conversation conversation;
        if (message.conversation instanceof Conversation) {
            conversation = (Conversation) message.conversation;
        } else {
            return false;
        }
        if (conversation.getMode() == Conversation.MODE_MULTI) {
            final Jid nextCounterpart = conversation.getNextCounterpart();
            return configurePrivateMessage(conversation, message, nextCounterpart, isFile);
        }
        return false;
    }

    public static boolean configurePrivateMessage(final Message message, final Jid counterpart) {
        final Conversation conversation;
        if (message.conversation instanceof Conversation) {
            conversation = (Conversation) message.conversation;
        } else {
            return false;
        }
        return configurePrivateMessage(conversation, message, counterpart, false);
    }

    private static boolean configurePrivateMessage(final Conversation conversation, final Message message, final Jid counterpart, final boolean isFile) {
        if (counterpart == null) {
            return false;
        }
        message.setCounterpart(counterpart);
        message.setTrueCounterpart(conversation.getMucOptions().getTrueCounterpart(counterpart));
        message.setType(isFile ? Message.TYPE_PRIVATE_FILE : Message.TYPE_PRIVATE);
        return true;
    }

    public int getResendCount(){
        return resendCount;
    }
    public int increaseResendCount(){
        return ++resendCount;
    }


    /**
     * Checks whether message should show an avatar next to it. Mainly used to hide the avatar
     * when succeeding messages have the same author. Thus it defaults to true.
     *
     * @return boolean
     */
    public boolean isAvatarable() {
        if (this.next() == null) {
            return true;
        }
        Message next = this.next();

        // same status (particularly sent vs received)
        if (this.getStatus() == next.getStatus()) {
            // same user
            if (this.getAvatarName().equals(next.getAvatarName())) {
                // same encryption
                if (this.getEncryption() != next.getEncryption()) {
                    return true;
                }
                // same day
                if (!UIHelper.sameDay(this.getTimeSent(), next.getTimeSent())){
                    return true;
                }
                // if merged, ask merged
                if (next.wasMergedIntoPrevious()) {
                    return next.isAvatarable();
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether message should show a username next to it. Mainly used to hide the username
     * when succeeding messages have the same author. Thus it defaults to true.
     *
     * @return boolean
     */
    public boolean showUsername() {
        if (this.prev() == null) {
            return true;
        }
        Message prev = this.prev();

        // same status (particularly sent vs received)
        if (this.getStatus() == prev.getStatus()) {
            // same user
            if (this.getAvatarName().equals(prev.getAvatarName())) {
                // same encryption
                if (this.getEncryption() != prev.getEncryption()) {
                    return true;
                }
                // same day
                if (!UIHelper.sameDay(this.getTimeSent(), prev.getTimeSent())){
                    return true;
                }
                // if merged, ask merged
                if (prev.wasMergedIntoPrevious()) {
                    return prev.isAvatarable();
                }
                return false;
            }
        }
        return true;
    }
}
