package de.thedevstack.piratx.xmpp;

import androidx.annotation.NonNull;

import com.google.common.base.Strings;

import im.conversations.android.annotation.XmlElement;
import im.conversations.android.xmpp.model.Extension;

@XmlElement(namespace = Fallback.NAMESPACE)
public class Fallback extends Extension {
    public static final String NAMESPACE = "urn:xmpp:fallback:0";

    public Fallback() {
        super(Fallback.class);
    }

    public Fallback(final String forWhat) {
        this();
        this.setFor(forWhat);
    }

    public String getFor() {
        return Strings.emptyToNull(this.getAttribute("for"));
    }

    public void setFor(@NonNull final String forWhat) {
        this.setAttribute("for", forWhat);
    }
}