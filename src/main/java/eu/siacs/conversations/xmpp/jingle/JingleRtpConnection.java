package eu.siacs.conversations.xmpp.jingle;

import android.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xml.Namespace;
import eu.siacs.conversations.xmpp.jingle.stanzas.Group;
import eu.siacs.conversations.xmpp.jingle.stanzas.IceUdpTransportInfo;
import eu.siacs.conversations.xmpp.jingle.stanzas.JinglePacket;
import eu.siacs.conversations.xmpp.jingle.stanzas.Reason;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import rocks.xmpp.addr.Jid;

public class JingleRtpConnection extends AbstractJingleConnection implements WebRTCWrapper.EventCallback {

    private static final Map<State, Collection<State>> VALID_TRANSITIONS;

    static {
        final ImmutableMap.Builder<State, Collection<State>> transitionBuilder = new ImmutableMap.Builder<>();
        transitionBuilder.put(State.NULL, ImmutableList.of(State.PROPOSED, State.SESSION_INITIALIZED));
        transitionBuilder.put(State.PROPOSED, ImmutableList.of(State.ACCEPTED, State.PROCEED, State.REJECTED, State.RETRACTED));
        transitionBuilder.put(State.PROCEED, ImmutableList.of(State.SESSION_INITIALIZED_PRE_APPROVED, State.TERMINATED_SUCCESS));
        transitionBuilder.put(State.SESSION_INITIALIZED, ImmutableList.of(State.SESSION_ACCEPTED, State.TERMINATED_CANCEL_OR_TIMEOUT, State.TERMINATED_DECLINED_OR_BUSY));
        transitionBuilder.put(State.SESSION_INITIALIZED_PRE_APPROVED, ImmutableList.of(State.SESSION_ACCEPTED, State.TERMINATED_CANCEL_OR_TIMEOUT, State.TERMINATED_DECLINED_OR_BUSY));
        transitionBuilder.put(State.SESSION_ACCEPTED, ImmutableList.of(State.TERMINATED_SUCCESS, State.TERMINATED_CONNECTIVITY_ERROR));
        VALID_TRANSITIONS = transitionBuilder.build();
    }

    private final WebRTCWrapper webRTCWrapper = new WebRTCWrapper(this);
    private final ArrayDeque<IceCandidate> pendingIceCandidates = new ArrayDeque<>();
    private State state = State.NULL;
    private RtpContentMap initiatorRtpContentMap;
    private RtpContentMap responderRtpContentMap;


    public JingleRtpConnection(JingleConnectionManager jingleConnectionManager, Id id, Jid initiator) {
        super(jingleConnectionManager, id, initiator);
    }

    private static State reasonToState(Reason reason) {
        switch (reason) {
            case SUCCESS:
                return State.TERMINATED_SUCCESS;
            case DECLINE:
            case BUSY:
                return State.TERMINATED_DECLINED_OR_BUSY;
            case CANCEL:
            case TIMEOUT:
                return State.TERMINATED_CANCEL_OR_TIMEOUT;
            default:
                return State.TERMINATED_CONNECTIVITY_ERROR;
        }
    }

    @Override
    void deliverPacket(final JinglePacket jinglePacket) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": packet delivered to JingleRtpConnection");
        switch (jinglePacket.getAction()) {
            case SESSION_INITIATE:
                receiveSessionInitiate(jinglePacket);
                break;
            case TRANSPORT_INFO:
                receiveTransportInfo(jinglePacket);
                break;
            case SESSION_ACCEPT:
                receiveSessionAccept(jinglePacket);
                break;
            case SESSION_TERMINATE:
                receiveSessionTerminate(jinglePacket);
                break;
            default:
                Log.d(Config.LOGTAG, String.format("%s: received unhandled jingle action %s", id.account.getJid().asBareJid(), jinglePacket.getAction()));
                break;
        }
    }

    private void receiveSessionTerminate(final JinglePacket jinglePacket) {
        final Reason reason = jinglePacket.getReason();
        final State previous = this.state;
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received session terminate reason=" + reason + " while in state " + previous);
        webRTCWrapper.close();
        transitionOrThrow(reasonToState(reason));
        if (previous == State.PROPOSED || previous == State.SESSION_INITIALIZED) {
            xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
        }
        jingleConnectionManager.finishConnection(this);
    }

    private void receiveTransportInfo(final JinglePacket jinglePacket) {
        if (isInState(State.SESSION_INITIALIZED, State.SESSION_INITIALIZED_PRE_APPROVED, State.SESSION_ACCEPTED)) {
            final RtpContentMap contentMap;
            try {
                contentMap = RtpContentMap.of(jinglePacket);
            } catch (IllegalArgumentException | NullPointerException e) {
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
                return;
            }
            final RtpContentMap rtpContentMap = isInitiator() ? this.responderRtpContentMap : this.initiatorRtpContentMap;
            final Group originalGroup = rtpContentMap != null ? rtpContentMap.group : null;
            final List<String> identificationTags = originalGroup == null ? Collections.emptyList() : originalGroup.getIdentificationTags();
            if (identificationTags.size() == 0) {
                Log.w(Config.LOGTAG, id.account.getJid().asBareJid() + ": no identification tags found in initial offer. we won't be able to calculate mLineIndices");
            }
            for (final Map.Entry<String, RtpContentMap.DescriptionTransport> content : contentMap.contents.entrySet()) {
                final String ufrag = content.getValue().transport.getAttribute("ufrag");
                for (final IceUdpTransportInfo.Candidate candidate : content.getValue().transport.getCandidates()) {
                    final String sdp = candidate.toSdpAttribute(ufrag);
                    final String sdpMid = content.getKey();
                    final int mLineIndex = identificationTags.indexOf(sdpMid);
                    final IceCandidate iceCandidate = new IceCandidate(sdpMid, mLineIndex, sdp);
                    if (isInState(State.SESSION_ACCEPTED)) {
                        Log.d(Config.LOGTAG, "received candidate: " + iceCandidate);
                        this.webRTCWrapper.addIceCandidate(iceCandidate);
                    } else {
                        this.pendingIceCandidates.offer(iceCandidate);
                        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": put ICE candidate on backlog");
                    }
                }
            }
        } else {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received transport info while in state=" + this.state);
        }
    }

    private void receiveSessionInitiate(final JinglePacket jinglePacket) {
        if (isInitiator()) {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate even though we were initiating", id.account.getJid().asBareJid()));
            //TODO respond with out-of-order
            return;
        }
        final RtpContentMap contentMap;
        try {
            contentMap = RtpContentMap.of(jinglePacket);
            contentMap.requireContentDescriptions();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
            return;
        }
        Log.d(Config.LOGTAG, "processing session-init with " + contentMap.contents.size() + " contents");
        final State target;
        if (this.state == State.PROCEED) {
            target = State.SESSION_INITIALIZED_PRE_APPROVED;
        } else {
            target = State.SESSION_INITIALIZED;
        }
        if (transition(target)) {
            this.initiatorRtpContentMap = contentMap;
            if (target == State.SESSION_INITIALIZED_PRE_APPROVED) {
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": automatically accepting session-initiate");
                sendSessionAccept();
            } else {
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received not pre-approved session-initiate. start ringing");
                startRinging();
            }
        } else {
            Log.d(Config.LOGTAG, String.format("%s: received session-initiate while in state %s", id.account.getJid().asBareJid(), state));
        }
    }

    private void receiveSessionAccept(final JinglePacket jinglePacket) {
        if (!isInitiator()) {
            Log.d(Config.LOGTAG, String.format("%s: received session-accept even though we were responding", id.account.getJid().asBareJid()));
            //TODO respond with out-of-order
            return;
        }
        final RtpContentMap contentMap;
        try {
            contentMap = RtpContentMap.of(jinglePacket);
            contentMap.requireContentDescriptions();
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": improperly formatted contents", e);
            return;
        }
        Log.d(Config.LOGTAG, "processing session-accept with " + contentMap.contents.size() + " contents");
        if (transition(State.SESSION_ACCEPTED)) {
            receiveSessionAccept(contentMap);
        } else {
            Log.d(Config.LOGTAG, String.format("%s: received session-accept while in state %s", id.account.getJid().asBareJid(), state));
            //TODO out-of-order
        }
    }

    private void receiveSessionAccept(final RtpContentMap contentMap) {
        this.responderRtpContentMap = contentMap;
        org.webrtc.SessionDescription answer = new org.webrtc.SessionDescription(
                org.webrtc.SessionDescription.Type.ANSWER,
                SessionDescription.of(contentMap).toString()
        );
        try {
            this.webRTCWrapper.setRemoteDescription(answer).get();
        } catch (Exception e) {
            Log.d(Config.LOGTAG, "unable to receive session accept", e);
        }
    }

    private void sendSessionAccept() {
        final RtpContentMap rtpContentMap = this.initiatorRtpContentMap;
        if (rtpContentMap == null) {
            throw new IllegalStateException("initiator RTP Content Map has not been set");
        }
        final SessionDescription offer;
        try {
            offer = SessionDescription.of(rtpContentMap);
        } catch (final IllegalArgumentException e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": unable to process offer", e);
            //TODO terminate session with application error
            return;
        }
        sendSessionAccept(offer);
    }

    private void sendSessionAccept(SessionDescription offer) {
        discoverIceServers(iceServers -> {
            setupWebRTC(iceServers);
            final org.webrtc.SessionDescription sdp = new org.webrtc.SessionDescription(
                    org.webrtc.SessionDescription.Type.OFFER,
                    offer.toString()
            );
            try {
                this.webRTCWrapper.setRemoteDescription(sdp).get();
                addIceCandidatesFromBlackLog();
                org.webrtc.SessionDescription webRTCSessionDescription = this.webRTCWrapper.createAnswer().get();
                final SessionDescription sessionDescription = SessionDescription.parse(webRTCSessionDescription.description);
                final RtpContentMap respondingRtpContentMap = RtpContentMap.of(sessionDescription);
                sendSessionAccept(respondingRtpContentMap);
                this.webRTCWrapper.setLocalDescription(webRTCSessionDescription);
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to send session accept", e);

            }
        });
    }

    private void addIceCandidatesFromBlackLog() {
        while (!this.pendingIceCandidates.isEmpty()) {
            final IceCandidate iceCandidate = this.pendingIceCandidates.poll();
            this.webRTCWrapper.addIceCandidate(iceCandidate);
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": added ICE candidate from back log " + iceCandidate);
        }
    }

    private void sendSessionAccept(final RtpContentMap rtpContentMap) {
        this.responderRtpContentMap = rtpContentMap;
        this.transitionOrThrow(State.SESSION_ACCEPTED);
        final JinglePacket sessionAccept = rtpContentMap.toJinglePacket(JinglePacket.Action.SESSION_ACCEPT, id.sessionId);
        Log.d(Config.LOGTAG, sessionAccept.toString());
        send(sessionAccept);
    }

    void deliveryMessage(final Jid from, final Element message) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": delivered message to JingleRtpConnection " + message);
        switch (message.getName()) {
            case "propose":
                receivePropose(from, message);
                break;
            case "proceed":
                receiveProceed(from, message);
                break;
            case "retract":
                receiveRetract(from, message);
                break;
            case "reject":
                receiveReject(from, message);
                break;
            case "accept":
                receiveAccept(from, message);
                break;
            default:
                break;
        }
    }

    private void receiveAccept(Jid from, Element message) {
        final boolean originatedFromMyself = from.asBareJid().equals(id.account.getJid().asBareJid());
        if (originatedFromMyself) {
            if (transition(State.ACCEPTED)) {
                this.xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
                this.jingleConnectionManager.finishConnection(this);
            } else {
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": unable to transition to accept because already in state=" + this.state);
            }
        } else {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": ignoring 'accept' from " + from);
        }
    }

    private void receiveReject(Jid from, Element message) {
        final boolean originatedFromMyself = from.asBareJid().equals(id.account.getJid().asBareJid());
        //reject from another one of my clients
        if (originatedFromMyself) {
            if (transition(State.REJECTED)) {
                this.xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
                this.jingleConnectionManager.finishConnection(this);
            } else {
                Log.d(Config.LOGTAG, "not able to transition into REJECTED because already in " + this.state);
            }
        } else {
            Log.d(Config.LOGTAG, id.account.getJid() + ": ignoring reject from " + from + " for session with " + id.with);
        }
    }

    private void receivePropose(final Jid from, final Element propose) {
        final boolean originatedFromMyself = from.asBareJid().equals(id.account.getJid().asBareJid());
        //TODO we can use initiator logic here
        if (originatedFromMyself) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": saw proposal from mysql. ignoring");
        } else if (transition(State.PROPOSED)) {
            startRinging();
        } else {
            Log.d(Config.LOGTAG, id.account.getJid() + ": ignoring session proposal because already in " + state);
        }
    }

    private void startRinging() {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received call from " + id.with + ". start ringing");
        xmppConnectionService.getNotificationService().showIncomingCallNotification(id);
    }

    private void receiveProceed(final Jid from, final Element proceed) {
        if (from.equals(id.with)) {
            if (isInitiator()) {
                if (transition(State.PROCEED)) {
                    this.sendSessionInitiate(State.SESSION_INITIALIZED_PRE_APPROVED);
                } else {
                    Log.d(Config.LOGTAG, String.format("%s: ignoring proceed because already in %s", id.account.getJid().asBareJid(), this.state));
                }
            } else {
                Log.d(Config.LOGTAG, String.format("%s: ignoring proceed because we were not initializing", id.account.getJid().asBareJid()));
            }
        } else if (from.asBareJid().equals(id.account.getJid().asBareJid())) {
            if (transition(State.ACCEPTED)) {
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": moved session with " + id.with + " into state accepted after received carbon copied procced");
                this.xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
                this.jingleConnectionManager.finishConnection(this);
            }
        } else {
            //TODO a carbon copied proceed from another client of mine has the same logic as `accept`
            Log.d(Config.LOGTAG, String.format("%s: ignoring proceed from %s. was expected from %s", id.account.getJid().asBareJid(), from, id.with));
        }
    }

    private void receiveRetract(final Jid from, final Element retract) {
        if (from.equals(id.with)) {
            if (transition(State.RETRACTED)) {
                xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": session with " + id.with + " has been retracted");
                //TODO create missed call notification/message
                jingleConnectionManager.finishConnection(this);
            } else {
                Log.d(Config.LOGTAG, "ignoring retract because already in " + this.state);
            }
        } else {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": received retract from " + from + ". expected retract from" + id.with + ". ignoring");
        }
    }

    private void sendSessionInitiate(final State targetState) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": prepare session-initiate");
        discoverIceServers(iceServers -> {
            setupWebRTC(iceServers);
            try {
                org.webrtc.SessionDescription webRTCSessionDescription = this.webRTCWrapper.createOffer().get();
                final SessionDescription sessionDescription = SessionDescription.parse(webRTCSessionDescription.description);
                Log.d(Config.LOGTAG, "description: " + webRTCSessionDescription.description);
                final RtpContentMap rtpContentMap = RtpContentMap.of(sessionDescription);
                sendSessionInitiate(rtpContentMap, targetState);
                this.webRTCWrapper.setLocalDescription(webRTCSessionDescription).get();
            } catch (Exception e) {
                Log.d(Config.LOGTAG, "unable to sendSessionInitiate", e);
            }
        });
    }

    private void sendSessionInitiate(RtpContentMap rtpContentMap, final State targetState) {
        this.initiatorRtpContentMap = rtpContentMap;
        this.transitionOrThrow(targetState);
        final JinglePacket sessionInitiate = rtpContentMap.toJinglePacket(JinglePacket.Action.SESSION_INITIATE, id.sessionId);
        Log.d(Config.LOGTAG, sessionInitiate.toString());
        send(sessionInitiate);
    }

    private void sendSessionTerminate(final Reason reason) {
        final State target = reasonToState(reason);
        transitionOrThrow(target);
        final JinglePacket jinglePacket = new JinglePacket(JinglePacket.Action.SESSION_TERMINATE, id.sessionId);
        jinglePacket.setReason(reason);
        send(jinglePacket);
        Log.d(Config.LOGTAG, jinglePacket.toString());
        jingleConnectionManager.finishConnection(this);
    }

    private void sendTransportInfo(final String contentName, IceUdpTransportInfo.Candidate candidate) {
        final RtpContentMap transportInfo;
        try {
            final RtpContentMap rtpContentMap = isInitiator() ? this.initiatorRtpContentMap : this.responderRtpContentMap;
            transportInfo = rtpContentMap.transportInfo(contentName, candidate);
        } catch (Exception e) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": unable to prepare transport-info from candidate for content=" + contentName);
            return;
        }
        final JinglePacket jinglePacket = transportInfo.toJinglePacket(JinglePacket.Action.TRANSPORT_INFO, id.sessionId);
        Log.d(Config.LOGTAG, jinglePacket.toString());
        send(jinglePacket);
    }

    private void send(final JinglePacket jinglePacket) {
        jinglePacket.setTo(id.with);
        //TODO track errors
        xmppConnectionService.sendIqPacket(id.account, jinglePacket, null);
    }

    public RtpEndUserState getEndUserState() {
        switch (this.state) {
            case PROPOSED:
            case SESSION_INITIALIZED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.INCOMING_CALL;
                }
            case PROCEED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.ACCEPTING_CALL;
                }
            case SESSION_INITIALIZED_PRE_APPROVED:
                if (isInitiator()) {
                    return RtpEndUserState.RINGING;
                } else {
                    return RtpEndUserState.CONNECTING;
                }
            case SESSION_ACCEPTED:
                final PeerConnection.PeerConnectionState state = webRTCWrapper.getState();
                if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                    return RtpEndUserState.CONNECTED;
                } else if (state == PeerConnection.PeerConnectionState.NEW || state == PeerConnection.PeerConnectionState.CONNECTING) {
                    return RtpEndUserState.CONNECTING;
                } else if (state == PeerConnection.PeerConnectionState.CLOSED) {
                    return RtpEndUserState.ENDING_CALL;
                } else if (state == PeerConnection.PeerConnectionState.FAILED) {
                    return RtpEndUserState.CONNECTIVITY_ERROR;
                } else {
                    return RtpEndUserState.ENDING_CALL;
                }
            case REJECTED:
            case TERMINATED_DECLINED_OR_BUSY:
                if (isInitiator()) {
                    return RtpEndUserState.DECLINED_OR_BUSY;
                } else {
                    return RtpEndUserState.ENDED;
                }
            case TERMINATED_SUCCESS:
            case ACCEPTED:
            case RETRACTED:
            case TERMINATED_CANCEL_OR_TIMEOUT:
                return RtpEndUserState.ENDED;
            case TERMINATED_CONNECTIVITY_ERROR:
                return RtpEndUserState.CONNECTIVITY_ERROR;
        }
        throw new IllegalStateException(String.format("%s has no equivalent EndUserState", this.state));
    }


    public void acceptCall() {
        switch (this.state) {
            case PROPOSED:
                acceptCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                acceptCallFromSessionInitialized();
                break;
            default:
                throw new IllegalStateException("Can not accept call from " + this.state);
        }
    }

    public void rejectCall() {
        switch (this.state) {
            case PROPOSED:
                rejectCallFromProposed();
                break;
            case SESSION_INITIALIZED:
                rejectCallFromSessionInitiate();
                break;
            default:
                throw new IllegalStateException("Can not reject call from " + this.state);
        }
    }

    public void endCall() {
        if (isInState(State.PROCEED)) {
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": ending call while in state PROCEED just means ending the connection");
            webRTCWrapper.close();
            jingleConnectionManager.finishConnection(this);
            transitionOrThrow(State.TERMINATED_SUCCESS); //arguably this wasn't success; but not a real failure either
            return;
        }
        if (isInitiator() && isInState(State.SESSION_INITIALIZED, State.SESSION_INITIALIZED_PRE_APPROVED)) {
            webRTCWrapper.close();
            sendSessionTerminate(Reason.CANCEL);
            return;
        }
        if (isInState(State.SESSION_INITIALIZED, State.SESSION_INITIALIZED_PRE_APPROVED, State.SESSION_ACCEPTED)) {
            webRTCWrapper.close();
            sendSessionTerminate(Reason.SUCCESS);
            return;
        }
        throw new IllegalStateException("called 'endCall' while in state " + this.state);
    }

    private void setupWebRTC(final List<PeerConnection.IceServer> iceServers) {
        this.webRTCWrapper.setup(this.xmppConnectionService);
        this.webRTCWrapper.initializePeerConnection(iceServers);
    }

    private void acceptCallFromProposed() {
        transitionOrThrow(State.PROCEED);
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
        this.sendJingleMessage("accept", id.account.getJid().asBareJid());
        this.sendJingleMessage("proceed");
    }

    private void rejectCallFromProposed() {
        transitionOrThrow(State.REJECTED);
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
        this.sendJingleMessage("reject");
        jingleConnectionManager.finishConnection(this);
    }

    private void rejectCallFromSessionInitiate() {
        webRTCWrapper.close();
        sendSessionTerminate(Reason.DECLINE);
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
    }

    private void sendJingleMessage(final String action) {
        sendJingleMessage(action, id.with);
    }

    private void sendJingleMessage(final String action, final Jid to) {
        final MessagePacket messagePacket = new MessagePacket();
        messagePacket.setType(MessagePacket.TYPE_CHAT); //we want to carbon copy those
        messagePacket.setTo(to);
        messagePacket.addChild(action, Namespace.JINGLE_MESSAGE).setAttribute("id", id.sessionId);
        Log.d(Config.LOGTAG, messagePacket.toString());
        xmppConnectionService.sendMessagePacket(id.account, messagePacket);
    }

    private void acceptCallFromSessionInitialized() {
        xmppConnectionService.getNotificationService().cancelIncomingCallNotification();
        sendSessionAccept();
    }

    private synchronized boolean isInState(State... state) {
        return Arrays.asList(state).contains(this.state);
    }

    private synchronized boolean transition(final State target) {
        final Collection<State> validTransitions = VALID_TRANSITIONS.get(this.state);
        if (validTransitions != null && validTransitions.contains(target)) {
            this.state = target;
            Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": transitioned into " + target);
            updateEndUserState();
            return true;
        } else {
            return false;
        }
    }

    public void transitionOrThrow(final State target) {
        if (!transition(target)) {
            throw new IllegalStateException(String.format("Unable to transition from %s to %s", this.state, target));
        }
    }

    @Override
    public void onIceCandidate(final IceCandidate iceCandidate) {
        final IceUdpTransportInfo.Candidate candidate = IceUdpTransportInfo.Candidate.fromSdpAttribute(iceCandidate.sdp);
        Log.d(Config.LOGTAG, "sending candidate: " + iceCandidate.toString());
        sendTransportInfo(iceCandidate.sdpMid, candidate);
    }

    @Override
    public void onConnectionChange(final PeerConnection.PeerConnectionState newState) {
        Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": PeerConnectionState changed to " + newState);
        updateEndUserState();
        if (newState == PeerConnection.PeerConnectionState.FAILED) { //TODO guard this in isState(initiated,initated_approved,accepted) otherwise it might fire too late
            sendSessionTerminate(Reason.CONNECTIVITY_ERROR);
        }
    }

    private void updateEndUserState() {
        xmppConnectionService.notifyJingleRtpConnectionUpdate(id.account, id.with, id.sessionId, getEndUserState());
    }

    private void discoverIceServers(final OnIceServersDiscovered onIceServersDiscovered) {
        if (id.account.getXmppConnection().getFeatures().extendedServiceDiscovery()) {
            final IqPacket request = new IqPacket(IqPacket.TYPE.GET);
            request.setTo(Jid.of(id.account.getJid().getDomain()));
            request.addChild("services", Namespace.EXTERNAL_SERVICE_DISCOVERY);
            xmppConnectionService.sendIqPacket(id.account, request, (account, response) -> {
                ImmutableList.Builder<PeerConnection.IceServer> listBuilder = new ImmutableList.Builder<>();
                if (response.getType() == IqPacket.TYPE.RESULT) {
                    final Element services = response.findChild("services", Namespace.EXTERNAL_SERVICE_DISCOVERY);
                    final List<Element> children = services == null ? Collections.emptyList() : services.getChildren();
                    for (final Element child : children) {
                        if ("service".equals(child.getName())) {
                            final String type = child.getAttribute("type");
                            final String host = child.getAttribute("host");
                            final String port = child.getAttribute("port");
                            final String transport = child.getAttribute("transport");
                            final String username = child.getAttribute("username");
                            final String password = child.getAttribute("password");
                            if (Arrays.asList("stun", "type").contains(type) && host != null && port != null && "udp".equals(transport)) {
                                PeerConnection.IceServer.Builder iceServerBuilder = PeerConnection.IceServer.builder(String.format("%s:%s:%s", type, host, port));
                                if (username != null && password != null) {
                                    iceServerBuilder.setUsername(username);
                                    iceServerBuilder.setPassword(password);
                                }
                                final PeerConnection.IceServer iceServer = iceServerBuilder.createIceServer();
                                Log.d(Config.LOGTAG, id.account.getJid().asBareJid() + ": discovered ICE Server: " + iceServer);
                                listBuilder.add(iceServer);
                            }
                        }
                    }
                }
                List<PeerConnection.IceServer> iceServers = listBuilder.build();
                if (iceServers.size() == 0) {
                    Log.w(Config.LOGTAG, id.account.getJid().asBareJid() + ": no ICE server found " + response);
                }
                onIceServersDiscovered.onIceServersDiscovered(iceServers);
            });
        } else {
            Log.w(Config.LOGTAG, id.account.getJid().asBareJid() + ": has no external service discovery");
            onIceServersDiscovered.onIceServersDiscovered(Collections.emptyList());
        }
    }

    private interface OnIceServersDiscovered {
        void onIceServersDiscovered(List<PeerConnection.IceServer> iceServers);
    }
}
