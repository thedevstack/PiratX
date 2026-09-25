package de.thedevstack.piratx.libsignal;

import eu.siacs.conversations.xmpp.Jid;

public class SignalProtocolAddress {

    public static org.signal.libsignal.protocol.SignalProtocolAddress newSignalProtocolAddress(Jid jid, int deviceId) {
        return newSignalProtocolAddress(jid.asBareJid().toString(), deviceId);
    }

    public static org.signal.libsignal.protocol.SignalProtocolAddress newSignalProtocolAddress(String bareJid, int deviceId) {
        int libsignalDeviceId = (deviceId - 1) % 127 + 1;// Ergebnis im Bereich 0..126, dann auf 1..127 schieben
        return new org.signal.libsignal.protocol.SignalProtocolAddress(bareJid, libsignalDeviceId);
    }
}
