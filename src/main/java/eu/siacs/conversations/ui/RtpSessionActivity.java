package eu.siacs.conversations.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.common.collect.ImmutableList;

import java.lang.ref.WeakReference;
import java.util.Arrays;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityRtpSessionBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.utils.PermissionUtils;
import eu.siacs.conversations.xmpp.jingle.AbstractJingleConnection;
import eu.siacs.conversations.xmpp.jingle.JingleRtpConnection;
import eu.siacs.conversations.xmpp.jingle.RtpEndUserState;
import rocks.xmpp.addr.Jid;

import static eu.siacs.conversations.utils.PermissionUtils.getFirstDenied;
import static java.util.Arrays.asList;

//TODO if last state was BUSY (or RETRY); we want to reset action to view or something so we don’t automatically call again on recreate

public class RtpSessionActivity extends XmppActivity implements XmppConnectionService.OnJingleRtpConnectionUpdate {

    private static final String PROXIMITY_WAKE_LOCK_TAG = "conversations:in-rtp-session";

    private static final int REQUEST_ACCEPT_CALL = 0x1111;

    public static final String EXTRA_WITH = "with";
    public static final String EXTRA_SESSION_ID = "session_id";
    public static final String EXTRA_LAST_REPORTED_STATE = "last_reported_state";

    public static final String ACTION_ACCEPT_CALL = "action_accept_call";
    public static final String ACTION_MAKE_VOICE_CALL = "action_make_voice_call";
    public static final String ACTION_MAKE_VIDEO_CALL = "action_make_video_call";

    private WeakReference<JingleRtpConnection> rtpConnectionReference;

    private ActivityRtpSessionBinding binding;
    private PowerManager.WakeLock mProximityWakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        Log.d(Config.LOGTAG, "RtpSessionActivity.onCreate()");
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_rtp_session);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(Config.LOGTAG, "RtpSessionActivity.onStart()");
    }

    private void endCall(View view) {
        endCall();
    }

    private void endCall() {
        if (this.rtpConnectionReference == null) {
            final Intent intent = getIntent();
            final Account account = extractAccount(intent);
            final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
            xmppConnectionService.getJingleConnectionManager().retractSessionProposal(account, with.asBareJid());
            finish();
        } else {
            requireRtpConnection().endCall();
        }
    }

    private void rejectCall(View view) {
        requireRtpConnection().rejectCall();
        finish();
    }

    private void acceptCall(View view) {
        requestPermissionsAndAcceptCall();
    }

    private void requestPermissionsAndAcceptCall() {
        if (PermissionUtils.hasPermission(this, ImmutableList.of(Manifest.permission.RECORD_AUDIO), REQUEST_ACCEPT_CALL)) {
            putScreenInCallMode();
            requireRtpConnection().acceptCall();
        }
    }

    @SuppressLint("WakelockTimeout")
    private void putScreenInCallMode() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.e(Config.LOGTAG, "power manager not available");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            this.mProximityWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, PROXIMITY_WAKE_LOCK_TAG);
            if (!this.mProximityWakeLock.isHeld()) {
                Log.d(Config.LOGTAG, "acquiring wake lock");
                this.mProximityWakeLock.acquire();
            }
        }
    }

    private void releaseWakeLock() {
        if (this.mProximityWakeLock != null && mProximityWakeLock.isHeld()) {
            Log.d(Config.LOGTAG, "releasing wake lock");
            this.mProximityWakeLock.release();
            this.mProximityWakeLock = null;
        }
    }

    @Override
    protected void refreshUiReal() {

    }

    @Override
    public void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        final String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (sessionId != null) {
            Log.d(Config.LOGTAG, "reinitializing from onNewIntent()");
            initializeActivityWithRunningRapSession(account, with, sessionId);
            if (ACTION_ACCEPT_CALL.equals(intent.getAction())) {
                Log.d(Config.LOGTAG, "accepting call from onNewIntent()");
                requestPermissionsAndAcceptCall();
                resetIntent(intent.getExtras());
            }
        } else {
            throw new IllegalStateException("received onNewIntent without sessionId");
        }
    }

    @Override
    void onBackendConnected() {
        final Intent intent = getIntent();
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        final String sessionId = intent.getStringExtra(EXTRA_SESSION_ID);
        if (sessionId != null) {
            initializeActivityWithRunningRapSession(account, with, sessionId);
            if (ACTION_ACCEPT_CALL.equals(intent.getAction())) {
                Log.d(Config.LOGTAG, "intent action was accept");
                requestPermissionsAndAcceptCall();
                resetIntent(intent.getExtras());
            }
        } else if (asList(ACTION_MAKE_VIDEO_CALL, ACTION_MAKE_VOICE_CALL).contains(intent.getAction())) {
            proposeJingleRtpSession(account, with);
            binding.with.setText(account.getRoster().getContact(with).getDisplayName());
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            final String extraLastState = intent.getStringExtra(EXTRA_LAST_REPORTED_STATE);
            if (extraLastState != null) {
                Log.d(Config.LOGTAG, "restored last state from intent extra");
                RtpEndUserState state = RtpEndUserState.valueOf(extraLastState);
                updateButtonConfiguration(state);
                updateStateDisplay(state);
            }
            binding.with.setText(account.getRoster().getContact(with).getDisplayName());
        }
    }

    private void proposeJingleRtpSession(final Account account, final Jid with) {
        xmppConnectionService.getJingleConnectionManager().proposeJingleRtpSession(account, with);
        putScreenInCallMode();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionUtils.allGranted(grantResults)) {
            if (requestCode == REQUEST_ACCEPT_CALL) {
                requireRtpConnection().acceptCall();
            }
        } else {
            @StringRes int res;
            final String firstDenied = getFirstDenied(grantResults, permissions);
            if (Manifest.permission.RECORD_AUDIO.equals(firstDenied)) {
                res = R.string.no_microphone_permission;
            } else if (Manifest.permission.CAMERA.equals(firstDenied)) {
                res = R.string.no_camera_permission;
            } else {
                throw new IllegalStateException("Invalid permission result request");
            }
            Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStop() {
        if (!isChangingConfigurations()) {
            releaseWakeLock();
            //TODO maybe we want to finish if call had ended
        }
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        endCall();
        super.onBackPressed();
    }


    private void initializeActivityWithRunningRapSession(final Account account, Jid with, String sessionId) {
        final WeakReference<JingleRtpConnection> reference = xmppConnectionService.getJingleConnectionManager()
                .findJingleRtpConnection(account, with, sessionId);
        if (reference == null || reference.get() == null) {
            finish();
            return;
        }
        this.rtpConnectionReference = reference;
        final RtpEndUserState currentState = requireRtpConnection().getEndUserState();
        if (currentState == RtpEndUserState.ENDED) {
            finish();
            return;
        }
        if (JingleRtpConnection.STATES_SHOWING_ONGOING_CALL.contains(requireRtpConnection().getState())) {
            putScreenInCallMode();
        }
        binding.with.setText(getWith().getDisplayName());
        updateStateDisplay(currentState);
        updateButtonConfiguration(currentState);
    }

    private void reInitializeActivityWithRunningRapSession(final Account account, Jid with, String sessionId) {
        runOnUiThread(() -> {
            initializeActivityWithRunningRapSession(account, with, sessionId);
        });
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(EXTRA_WITH, with.toEscapedString());
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        setIntent(intent);
    }

    private void updateStateDisplay(final RtpEndUserState state) {
        switch (state) {
            case INCOMING_CALL:
                binding.status.setText(R.string.rtp_state_incoming_call);
                break;
            case CONNECTING:
                binding.status.setText(R.string.rtp_state_connecting);
                break;
            case CONNECTED:
                binding.status.setText(R.string.rtp_state_connected);
                break;
            case ACCEPTING_CALL:
                binding.status.setText(R.string.rtp_state_accepting_call);
                break;
            case ENDING_CALL:
                binding.status.setText(R.string.rtp_state_ending_call);
                break;
            case FINDING_DEVICE:
                binding.status.setText(R.string.rtp_state_finding_device);
                break;
            case RINGING:
                binding.status.setText(R.string.rtp_state_ringing);
                break;
            case DECLINED_OR_BUSY:
                binding.status.setText(R.string.rtp_state_declined_or_busy);
                break;
            case CONNECTIVITY_ERROR:
                binding.status.setText(R.string.rtp_state_connectivity_error);
                break;
            case APPLICATION_ERROR:
                binding.status.setText(R.string.rtp_state_application_failure);
                break;
            case ENDED:
                throw new IllegalStateException("Activity should have called finishAndReleaseWakeLock();");
            default:
                throw new IllegalStateException(String.format("State %s has not been handled in UI", state));
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateButtonConfiguration(final RtpEndUserState state) {
        if (state == RtpEndUserState.INCOMING_CALL) {
            this.binding.rejectCall.setOnClickListener(this::rejectCall);
            this.binding.rejectCall.setImageResource(R.drawable.ic_call_end_white_48dp);
            this.binding.rejectCall.setVisibility(View.VISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setOnClickListener(this::acceptCall);
            this.binding.acceptCall.setImageResource(R.drawable.ic_call_white_48dp);
            this.binding.acceptCall.setVisibility(View.VISIBLE);
        } else if (state == RtpEndUserState.ENDING_CALL) {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        } else if (state == RtpEndUserState.DECLINED_OR_BUSY) {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setOnClickListener(this::exit);
            this.binding.endCall.setImageResource(R.drawable.ic_clear_white_48dp);
            this.binding.endCall.setVisibility(View.VISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        } else if (state == RtpEndUserState.CONNECTIVITY_ERROR || state == RtpEndUserState.APPLICATION_ERROR) {
            this.binding.rejectCall.setOnClickListener(this::exit);
            this.binding.rejectCall.setImageResource(R.drawable.ic_clear_white_48dp);
            this.binding.rejectCall.setVisibility(View.VISIBLE);
            this.binding.endCall.setVisibility(View.INVISIBLE);
            this.binding.acceptCall.setOnClickListener(this::retry);
            this.binding.acceptCall.setImageResource(R.drawable.ic_replay_white_48dp);
            this.binding.acceptCall.setVisibility(View.VISIBLE);
        } else {
            this.binding.rejectCall.setVisibility(View.INVISIBLE);
            this.binding.endCall.setOnClickListener(this::endCall);
            this.binding.endCall.setImageResource(R.drawable.ic_call_end_white_48dp);
            this.binding.endCall.setVisibility(View.VISIBLE);
            this.binding.acceptCall.setVisibility(View.INVISIBLE);
        }
    }

    private void retry(View view) {
        Log.d(Config.LOGTAG, "attempting retry");
        final Intent intent = getIntent();
        final Account account = extractAccount(intent);
        final Jid with = Jid.of(intent.getStringExtra(EXTRA_WITH));
        this.rtpConnectionReference = null;
        proposeJingleRtpSession(account, with);
    }

    private void exit(View view) {
        finish();
    }

    private Contact getWith() {
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        final Account account = id.account;
        return account.getRoster().getContact(id.with);
    }

    private JingleRtpConnection requireRtpConnection() {
        final JingleRtpConnection connection = this.rtpConnectionReference != null ? this.rtpConnectionReference.get() : null;
        if (connection == null) {
            throw new IllegalStateException("No RTP connection found");
        }
        return connection;
    }

    @Override
    public void onJingleRtpConnectionUpdate(Account account, Jid with, final String sessionId, RtpEndUserState state) {
        if (Arrays.asList(RtpEndUserState.APPLICATION_ERROR, RtpEndUserState.DECLINED_OR_BUSY, RtpEndUserState.DECLINED_OR_BUSY).contains(state)) {
            releaseWakeLock();
        }
        Log.d(Config.LOGTAG, "onJingleRtpConnectionUpdate(" + state + ")");
        if (with.isBareJid()) {
            updateRtpSessionProposalState(account, with, state);
            return;
        }
        if (this.rtpConnectionReference == null) {
            //this happens when going from proposed session to actual session
            reInitializeActivityWithRunningRapSession(account, with, sessionId);
            return;
        }
        final AbstractJingleConnection.Id id = requireRtpConnection().getId();
        if (account == id.account && id.with.equals(with) && id.sessionId.equals(sessionId)) {
            if (state == RtpEndUserState.ENDED) {
                finish();
                return;
            } else if (asList(RtpEndUserState.APPLICATION_ERROR, RtpEndUserState.DECLINED_OR_BUSY, RtpEndUserState.CONNECTIVITY_ERROR).contains(state)) {
                resetIntent(account, with, state);
            }
            runOnUiThread(() -> {
                updateStateDisplay(state);
                updateButtonConfiguration(state);
            });
        } else {
            Log.d(Config.LOGTAG, "received update for other rtp session");
            //TODO if we only ever have one; we might just switch over? Maybe!
        }
    }

    private void updateRtpSessionProposalState(final Account account, final Jid with, final RtpEndUserState state) {
        final Intent currentIntent = getIntent();
        final String withExtra = currentIntent == null ? null : currentIntent.getStringExtra(EXTRA_WITH);
        if (withExtra == null) {
            return;
        }
        if (Jid.ofEscaped(withExtra).asBareJid().equals(with)) {
            runOnUiThread(() -> {
                updateStateDisplay(state);
                updateButtonConfiguration(state);
            });
            resetIntent(account, with, state);
        }
    }

    private void resetIntent(final Bundle extras) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtras(extras);
        setIntent(intent);
    }

    private void resetIntent(final Account account, Jid with, final RtpEndUserState state) {
        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.putExtra(EXTRA_WITH, with.asBareJid().toEscapedString());
        intent.putExtra(EXTRA_ACCOUNT, account.getJid().toEscapedString());
        intent.putExtra(EXTRA_LAST_REPORTED_STATE, state.toString());
        setIntent(intent);
    }
}
