package de.thedevstack.piratx.utils;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.util.Log;

import eu.siacs.conversations.Conversations;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.xml.Element;

public class PiratXMessageUtil {
    public static SpannableStringBuilder adjustBodyIfNecessary(Message message, SpannableStringBuilder body) {
        if (message.isDeleted() && message.getType() == Message.TYPE_TEXT) {
            Log.d("Adjusting", "message is deleted");
            body = createRetractionBody(message);
        } else if (null != message.getPayloads()) {
            for (Element payload : message.getPayloads()) {
                Log.d("Adjusting", payload.toString());
                if ("urn:xmpp:message-retract:1".equals(payload.getNamespace())) {
                    body = createRetractionBody(message);

                    break;
                }
            }
        }

        return body;
    }

    protected static SpannableStringBuilder createRetractionBody(Message message) {
        String retractMessage = Conversations.getContext().getString(R.string.message_retracted);
        message.setBody(retractMessage);
        SpannableStringBuilder body = message.getSpannableBody();
        body.setSpan(new StyleSpan(Typeface.ITALIC), 0, retractMessage.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return body;
    }
}
