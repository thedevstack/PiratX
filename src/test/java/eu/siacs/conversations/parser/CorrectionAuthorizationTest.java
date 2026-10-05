package eu.siacs.conversations.parser;

import org.junit.Assert;
import org.junit.Test;

/**
 * Covers {@link MessageParser#mayReplace} - the rule that decides whether an incoming correction,
 * retraction or moderation is allowed to rewrite a message already in the conversation.
 *
 * <p>Getting this wrong lets one participant edit or delete another's messages, so each clause is
 * pinned individually rather than only in combination.
 */
public class CorrectionAuthorizationTest {

    private static boolean directChat(final boolean fingerprintsMatch) {
        return MessageParser.mayReplace(
                fingerprintsMatch, false, false, false, /* conversationMultiMode= */ false, false);
    }

    private static boolean conference(
            final boolean fingerprintsMatch,
            final boolean trueCounters,
            final boolean occupantId,
            final boolean mucUser,
            final boolean fromRoomItself) {
        return MessageParser.mayReplace(
                fingerprintsMatch, trueCounters, occupantId, mucUser, true, fromRoomItself);
    }

    // ---- encryption has to line up in every case ----

    /** A plaintext stanza must not be able to rewrite an encrypted message. */
    @Test
    public void mismatchedFingerprintsBlockADirectChatCorrection() {
        Assert.assertFalse(directChat(false));
    }

    @Test
    public void mismatchedFingerprintsBlockEvenAFullyIdentifiedSender() {
        Assert.assertFalse(conference(false, true, true, true, true));
    }

    @Test
    public void mismatchedFingerprintsBlockModeration() {
        Assert.assertFalse(conference(false, false, false, false, true));
    }

    // ---- one to one ----

    /** The conversation is keyed by the peer, so identity is already established. */
    @Test
    public void directChatNeedsOnlyMatchingFingerprints() {
        Assert.assertTrue(directChat(true));
    }

    // ---- conference: identity must come from the room ----

    /**
     * The case that matters: a conference message with matching encryption but nothing tying the
     * sender to the original author. It must not be allowed to rewrite it.
     */
    @Test
    public void conferenceCorrectionWithoutAnyIdentitySignalIsRejected() {
        Assert.assertFalse(conference(true, false, false, false, false));
    }

    @Test
    public void occupantIdMatchAuthorisesACorrection() {
        Assert.assertTrue(conference(true, false, true, false, false));
    }

    @Test
    public void resolvedRealAddressMatchAuthorisesACorrection() {
        Assert.assertTrue(conference(true, true, false, false, false));
    }

    @Test
    public void roomSuppliedUserRecordAuthorisesACorrection() {
        Assert.assertTrue(conference(true, false, false, true, false));
    }

    /** XEP-0425 moderation arrives from the bare room address and may retract anybody's message. */
    @Test
    public void moderationFromTheRoomItselfIsAuthorised() {
        Assert.assertTrue(conference(true, false, false, false, true));
    }

    /** Each identity signal stands alone; none of them is required alongside another. */
    @Test
    public void anySingleIdentitySignalSuffices() {
        Assert.assertTrue(conference(true, true, false, false, false));
        Assert.assertTrue(conference(true, false, true, false, false));
        Assert.assertTrue(conference(true, false, false, true, false));
        Assert.assertTrue(conference(true, false, false, false, true));
    }

    /**
     * Exhaustive: with matching fingerprints in a conference, permission is exactly "at least one
     * identity signal". Guards against a future clause quietly widening the rule.
     */
    @Test
    public void conferencePermissionIsExactlyTheDisjunctionOfIdentitySignals() {
        for (int mask = 0; mask < 16; mask++) {
            final boolean trueCounters = (mask & 1) != 0;
            final boolean occupantId = (mask & 2) != 0;
            final boolean mucUser = (mask & 4) != 0;
            final boolean fromRoom = (mask & 8) != 0;
            final boolean expected = trueCounters || occupantId || mucUser || fromRoom;
            Assert.assertEquals(
                    "mask " + mask,
                    expected,
                    conference(true, trueCounters, occupantId, mucUser, fromRoom));
            Assert.assertFalse(
                    "fingerprint mismatch must dominate, mask " + mask,
                    conference(false, trueCounters, occupantId, mucUser, fromRoom));
        }
    }

    // ---- editFingerprintsMatch: which edits may touch an encrypted original ----

    private static final int OMEMO2 = eu.siacs.conversations.entities.Message.ENCRYPTION_AXOLOTL_OMEMO2;
    private static final int LEGACY = eu.siacs.conversations.entities.Message.ENCRYPTION_AXOLOTL;
    private static final int NONE = eu.siacs.conversations.entities.Message.ENCRYPTION_NONE;

    @Test
    public void unencryptedOriginalAcceptsAnyEdit() {
        Assert.assertTrue(MessageParser.editFingerprintsMatch(null, NONE, null, false, false));
        Assert.assertTrue(MessageParser.editFingerprintsMatch(null, NONE, "fp", true, false));
    }

    @Test
    public void sameFingerprintMayCorrectAndRetract() {
        Assert.assertTrue(MessageParser.editFingerprintsMatch("fp", OMEMO2, "fp", false, false));
        Assert.assertTrue(MessageParser.editFingerprintsMatch("fp", OMEMO2, "fp", true, false));
    }

    /** The tightened rule: a cleartext (or foreign-key) retraction can't delete an OMEMO2 message. */
    @Test
    public void foreignRetractionCannotDeleteAnOmemo2Message() {
        Assert.assertFalse(MessageParser.editFingerprintsMatch("fp", OMEMO2, null, true, false));
        Assert.assertFalse(MessageParser.editFingerprintsMatch("fp", OMEMO2, "other", true, false));
    }

    @Test
    public void foreignCorrectionIsRejectedEvenForLegacyOrModeration() {
        Assert.assertFalse(MessageParser.editFingerprintsMatch("fp", OMEMO2, null, false, false));
        Assert.assertFalse(MessageParser.editFingerprintsMatch("fp", LEGACY, null, false, false));
        Assert.assertFalse(MessageParser.editFingerprintsMatch("fp", OMEMO2, null, false, true));
    }

    /** XEP-0425: the room may moderate any message, encrypted or not. */
    @Test
    public void roomModerationMayRetractAnEncryptedMessage() {
        Assert.assertTrue(MessageParser.editFingerprintsMatch("fp", OMEMO2, null, true, true));
    }

    /** Legacy OMEMO can't encrypt retractions, so they stay accepted in cleartext. */
    @Test
    public void legacyOmemoMessageStillAcceptsCleartextRetraction() {
        Assert.assertTrue(MessageParser.editFingerprintsMatch("fp", LEGACY, null, true, false));
    }
}
