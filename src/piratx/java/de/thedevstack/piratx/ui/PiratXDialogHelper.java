package de.thedevstack.piratx.ui;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import eu.siacs.conversations.entities.Message;

public class PiratXDialogHelper {
    public static void showMessageDetailsDialog(Context context, Message message) {
        // StringBuilder verwenden, um die Nachricht zu formatieren
        StringBuilder body = new StringBuilder();
        String from = message.getConversation().getAccount().getJid().toString();
        String to = message.getCounterpart().toString();
        if (message.getStatus() > Message.STATUS_RECEIVED) {
            from = message.getCounterpart().toString();
            to = message.getConversation().getAccount().getJid().toString();
        }

        body.append("Von: ").append(from).append("\n");
        body.append("An: ").append(to).append("\n");
        body.append("Zeit: ").append(message.getTimeReceived()).append("\n");
        body.append("UUID: ").append(message.getUuid()).append("\n");
        body.append("Server-ID: ").append(message.getServerMsgId()).append("\n");
        body.append("Remote-ID: ").append(message.getRemoteMsgId()).append("\n");
        body.append("Carbon: ").append(message.isCarbon()).append("\n");
        //body.append("Carbon: ").append(message.get).append("\n");

        // AlertDialog.Builder verwenden, um den Dialog zu erstellen
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle("Nachrichtendetails"); // Titel setzen
        builder.setMessage(body.toString()); // Die formatierte Nachricht setzen

        // Den OK-Button hinzufügen
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Der Klick auf den Button schließt den Dialog
            dialog.dismiss();
        });

        // Den Dialog anzeigen
        builder.show();
    }
}
