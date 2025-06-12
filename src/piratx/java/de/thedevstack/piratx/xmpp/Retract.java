package de.thedevstack.piratx.xmpp;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Retract.NAMESPACE)
public class Retract extends Extension {
    public static final String NAMESPACE = "urn:xmpp:message-retract:1";

    public Retract() {
        super(Retract.class);
    }

    public Retract(final String id) {
        this();
        this.setId(id);
    }

    public String getId() {
        return Strings.emptyToNull(this.getAttribute("id"));
    }

    public void setId(@NonNull final String id) {
        this.setAttribute("id", id);
    }
}
