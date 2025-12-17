package de.thedevstack.piratx.ui;

import android.app.PendingIntent;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.ContactDetailsActivity;
import eu.siacs.conversations.ui.UiCallback;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.xmpp.pep.Avatar;

public class PiratXContactDetailsActivityUtil {

    public static void loadAvatarFromXmpp(ContactDetailsActivity contactDetailsActivity, XmppConnectionService xmppConnectionService, Contact contact, ImageView imageView) {
        Log.d("PiratX", "Trying to fetch avatar");
        Avatar avatar = contact.getAvatar();

        if (null == avatar) {
            avatar = new Avatar();
            avatar.owner = contact.getJid().asBareJid();
            avatar.origin = Avatar.Origin.VCARD;
        }

        xmppConnectionService.fetchAvatar(contact.getAccount(), avatar, new UiCallback<Avatar>() {
            @Override
            public void success(Avatar object) {
                Log.d("PiratX", "Avatar fetched successfully");
                contactDetailsActivity.runOnUiThread(() -> {
                    Toast.makeText(contactDetailsActivity, "avatar loaded successfully", Toast.LENGTH_SHORT).show();
                    AvatarWorkerTask.loadAvatar(
                            contact, imageView, R.dimen.avatar_on_details_screen_size);
                        });
            }

            @Override
            public void error(int errorCode, Avatar object) {
                Log.d("PiratX", "Avatar fetch failed: " + errorCode);
                contactDetailsActivity.runOnUiThread(() -> Toast.makeText(contactDetailsActivity, errorCode, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void userInputRequired(PendingIntent pi, Avatar object) {
                Log.d("PiratX", "Avatar: User Input required");
            }
        });
    }
}
