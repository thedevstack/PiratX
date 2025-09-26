package eu.siacs.conversations.ui.adapter;

import static android.view.View.GONE;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.text.Editable;
import android.text.Spanned;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.ImageSpan;
import android.text.style.ClickableSpan;
import android.text.format.DateUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.util.LruCache;
import android.view.accessibility.AccessibilityEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.databinding.DataBindingUtil;
import androidx.media3.common.util.Log;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.github.pgreze.reactions.ReactionPopup;
import com.github.pgreze.reactions.ReactionsConfig;
import com.github.pgreze.reactions.ReactionsConfigBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.ShapeAppearanceModel;

import de.monocles.chat.BobTransfer;
import de.monocles.chat.InlineImageSpan;
import de.monocles.chat.LinkClickDetector;
import de.monocles.chat.MessageTextActionModeCallback;
import de.monocles.chat.Util;
import de.monocles.chat.WebxdcPage;
import de.monocles.chat.WebxdcUpdate;
import de.monocles.chat.EmojiSearch;
import de.monocles.chat.GetThumbnailForCid;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import com.google.common.collect.ImmutableSet;
import com.daimajia.swipe.SwipeLayout;
import com.lelloman.identicon.view.GithubIdenticonView;

import eu.siacs.conversations.ui.AddReactionActivity;
import io.ipfs.cid.Cid;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.saket.bettermovementmethod.BetterLinkMovementMethod;

import net.fellbaum.jemoji.EmojiManager;

import eu.siacs.conversations.AppSettings;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.axolotl.FingerprintStatus;
import eu.siacs.conversations.databinding.ItemMessageDateBubbleBinding;
import eu.siacs.conversations.databinding.ItemMessageRtpSessionBinding;
import eu.siacs.conversations.databinding.ItemMessageStatusBinding;
import eu.siacs.conversations.databinding.LinkDescriptionBinding;
import eu.siacs.conversations.databinding.ItemMessageEndBinding;
import eu.siacs.conversations.databinding.ItemMessageStartBinding;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.entities.Message.FileParams;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.entities.MucOptions;
import eu.siacs.conversations.entities.Reaction;
import eu.siacs.conversations.entities.RtpSessionStatus;
import eu.siacs.conversations.entities.Transferable;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.MessageArchiveService;
import eu.siacs.conversations.services.NotificationService;
import eu.siacs.conversations.ui.Activities;
import eu.siacs.conversations.ui.BindingAdapters;
import eu.siacs.conversations.ui.ConversationFragment;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.XmppActivity;
import eu.siacs.conversations.ui.service.AudioPlayer;
import eu.siacs.conversations.ui.text.DividerSpan;
import eu.siacs.conversations.ui.text.FixedURLSpan;
import eu.siacs.conversations.ui.text.QuoteSpan;
import eu.siacs.conversations.ui.util.Attachment;
import eu.siacs.conversations.ui.util.AvatarWorkerTask;
import eu.siacs.conversations.ui.util.MyLinkify;
import eu.siacs.conversations.ui.util.QuoteHelper;
import eu.siacs.conversations.ui.util.ShareUtil;
import eu.siacs.conversations.ui.util.ViewUtil;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.Emoticons;
import eu.siacs.conversations.utils.GeoHelper;
import eu.siacs.conversations.utils.MessageUtils;
import eu.siacs.conversations.utils.StylingHelper;
import eu.siacs.conversations.utils.TimeFrameUtils;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.mam.MamReference;
import eu.siacs.conversations.xml.Element;

import java.util.Arrays;
import java.util.Collection;

public class MessageAdapter extends ArrayAdapter<Message> {

    public static final String DATE_SEPARATOR_BODY = "DATE_SEPARATOR";
    private static final int END = 0;
    private static final int START = 1;
    private static final int STATUS = 2;
    private static final int DATE_SEPARATOR = 3;
    private static final int RTP_SESSION = 4;
    private final XmppActivity activity;
    private final AudioPlayer audioPlayer;
    private List<String> highlightedTerm = null;
    private final DisplayMetrics metrics;
    private ConversationFragment mConversationFragment = null;
    private OnContactPictureClicked mOnContactPictureClickedListener;
    private OnContactPictureClicked mOnMessageBoxClickedListener;
    private OnContactPictureLongClicked mOnContactPictureLongClickedListener;
    private OnInlineImageLongClicked mOnInlineImageLongClickedListener;
    private BubbleDesign bubbleDesign = new BubbleDesign(false, false, false, true);
    private final boolean mForceNames;
    private final Map<String, WebxdcUpdate> lastWebxdcUpdate = new HashMap<>();
    private String selectionUuid = null;
    private final AppSettings appSettings;
    private ReplyClickListener replyClickListener;


    private final float imagePreviewWidthTarget;
    private final float bubbleRadiusDim;
    private final float imageRadiusDim;
    private final float density;
    private final float padding8dp;
    private final float padding22dp;
    private final float targetImageWidthSmallThreshold;
    private final float targetImageWidthLargeThreshold;

    public MessageAdapter(
            final XmppActivity activity, final List<Message> messages, final boolean forceNames) {
        super(activity, 0, messages);
        this.density = activity.getResources().getDisplayMetrics().density;
        this.imagePreviewWidthTarget = activity.getResources().getDimension(R.dimen.image_preview_width);
        this.bubbleRadiusDim = activity.getResources().getDimension(R.dimen.bubble_radius);
        this.imageRadiusDim = activity.getResources().getDimension(R.dimen.image_radius);
        this.padding8dp = 8 * this.density;
        this.padding22dp = 22 * this.density;
        this.targetImageWidthSmallThreshold = 110 * this.density;
        this.targetImageWidthLargeThreshold = 200 * this.density;
        this.audioPlayer = new AudioPlayer(this);
        this.activity = activity;
        metrics = getContext().getResources().getDisplayMetrics();
        appSettings = new AppSettings(activity);
        updatePreferences();
        this.mForceNames = forceNames;
    }

    public MessageAdapter(final XmppActivity activity, final List<Message> messages) {
        this(activity, messages, false);
    }

    private static void resetClickListener(View... views) {
        for (View view : views) {
            if (view != null) view.setOnClickListener(null);
        }
    }

    public void flagScreenOn() {
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void flagScreenOff() {
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void setVolumeControl(final int stream) {
        activity.setVolumeControlStream(stream);
    }

    public void setOnContactPictureClicked(OnContactPictureClicked listener) {
        this.mOnContactPictureClickedListener = listener;
    }

    public void setOnMessageBoxClicked(OnContactPictureClicked listener) {
        this.mOnMessageBoxClickedListener = listener;
    }

    public void setReplyClickListener(ReplyClickListener listener) {
        this.replyClickListener = listener;
    }

    public void setConversationFragment(ConversationFragment frag) {
        mConversationFragment = frag;
    }

    public void quoteText(String text) {
        if (mConversationFragment != null) mConversationFragment.quoteText(text);
    }

    public boolean hasSelection() {
        return selectionUuid != null;
    }

    public Activity getActivity() {
        return activity;
    }

    public void setOnContactPictureLongClicked(OnContactPictureLongClicked listener) {
        this.mOnContactPictureLongClickedListener = listener;
    }

    public void setOnInlineImageLongClicked(OnInlineImageLongClicked listener) {
        this.mOnInlineImageLongClickedListener = listener;
    }

    @Override
    public int getViewTypeCount() {
        return 5;
    }

    private static int getItemViewType(final Message message, final boolean alignStart) {
        if (message.getType() == Message.TYPE_STATUS) {
            if (DATE_SEPARATOR_BODY.equals(message.getBody())) {
                return DATE_SEPARATOR;
            } else {
                return STATUS;
            }
        } else if (message.getType() == Message.TYPE_RTP_SESSION) {
            return RTP_SESSION;
        } else if (message.getStatus() <= Message.STATUS_RECEIVED || alignStart) {
            return START;
        } else {
            return END;
        }
    }

    @Override
    public int getItemViewType(final int position) {
        return getItemViewType(Objects.requireNonNull(getItem(position)), bubbleDesign.alignStart);
    }


    private void displayStatus(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        final int status = message.getStatus();
        final boolean error;
        final Transferable transferable = message.getTransferable();

        final boolean sent = status != Message.STATUS_RECEIVED;
        final boolean showUserNickname =
                message.getConversation().getMode() == Conversation.MODE_MULTI
                        && viewHolder instanceof StartBubbleMessageItemViewHolder;
        final String fileSize;
        if (message.isFileOrImage()
                || transferable != null
                || MessageUtils.unInitiatedButKnownSize(message)) {
            final FileParams params = message.getFileParams();
            fileSize = params.size != null ? UIHelper.filesizeToString(params.size) : null;
            if (message.getStatus() == Message.STATUS_SEND_FAILED
                    || (transferable != null
                    && (transferable.getStatus() == Transferable.STATUS_FAILED
                    || transferable.getStatus()
                    == Transferable.STATUS_CANCELLED))) {
                error = true;
            } else {
                error = message.getStatus() == Message.STATUS_SEND_FAILED;
            }
        } else {
            fileSize = null;
            error = message.getStatus() == Message.STATUS_SEND_FAILED;
        }

        if (sent) {
            final @DrawableRes Integer receivedIndicator =
                    getMessageStatusAsDrawable(message, status);
            if (receivedIndicator == null) {
                viewHolder.indicatorReceived().setVisibility(View.INVISIBLE);
            } else {
                viewHolder.indicatorReceived().setImageResource(receivedIndicator);
                if (status == Message.STATUS_SEND_FAILED) {
                    setImageTintError(viewHolder.indicatorReceived());
                } else {
                    setImageTint(viewHolder.indicatorReceived(), bubbleColor);
                }
                viewHolder.indicatorReceived().setVisibility(View.VISIBLE);
            }
        } else {
            viewHolder.indicatorReceived().setVisibility(View.GONE);
        }
        final var additionalStatusInfo = getAdditionalStatusInfo(message, status);

        if (error && sent) {
            viewHolder
                    .time()
                    .setTextColor(
                            MaterialColors.getColor(
                                    viewHolder.time(), androidx.appcompat.R.attr.colorError));
        } else {
            setTextColor(viewHolder.time(), bubbleColor);
        }
        setTextColor(viewHolder.subject(), bubbleColor);
        if (message.getEncryption() == Message.ENCRYPTION_NONE) {
            viewHolder.indicatorSecurity().setVisibility(View.GONE);
        } else {
            boolean verified = false;
            if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL) {
                final FingerprintStatus fingerprintStatus =
                        message.getConversation()
                                .getAccount()
                                .getAxolotlService()
                                .getFingerprintTrust(message.getFingerprint());
                if (fingerprintStatus != null && fingerprintStatus.isVerified()) {
                    verified = true;
                }
            }
            if (verified) {
                viewHolder.indicatorSecurity().setImageResource(R.drawable.ic_verified_user_24dp);
            } else {
                viewHolder.indicatorSecurity().setImageResource(R.drawable.ic_lock_24dp);
            }
            if (error && sent) {
                setImageTintError(viewHolder.indicatorSecurity());
            } else {
                setImageTint(viewHolder.indicatorSecurity(), bubbleColor);
            }
            viewHolder.indicatorSecurity().setVisibility(View.VISIBLE);
        }

        if (message.edited()) {
            viewHolder.indicatorEdit().setVisibility(View.VISIBLE);
            if (error && sent) {
                setImageTintError(viewHolder.indicatorEdit());
            } else {
                setImageTint(viewHolder.indicatorEdit(), bubbleColor);
            }
        } else {
            viewHolder.indicatorEdit().setVisibility(View.GONE);
        }

        final String formattedTime =
                UIHelper.readableTimeDifferenceFull(getContext(), message.getTimeSent());
        final String bodyLanguage = message.getBodyLanguage();
        final ImmutableList.Builder<String> timeInfoBuilder = new ImmutableList.Builder<>();
        if (message.getStatus() <= Message.STATUS_RECEIVED) {
            timeInfoBuilder.add(formattedTime);
            if (fileSize != null) {
                timeInfoBuilder.add(fileSize);
            }
            if (bodyLanguage != null) {
                timeInfoBuilder.add(bodyLanguage.toUpperCase(Locale.US));
            }
        } else {
            if (bodyLanguage != null) {
                timeInfoBuilder.add(bodyLanguage.toUpperCase(Locale.US));
            }
            if (fileSize != null) {
                timeInfoBuilder.add(fileSize);
            }
            // for space reasons we display only 'additional status info' (send progress or concrete
            // failure reason) or the time
            if (additionalStatusInfo != null) {
                timeInfoBuilder.add(additionalStatusInfo);
            } else {
                timeInfoBuilder.add(formattedTime);
            }
        }
        final var timeInfo = timeInfoBuilder.build();
        viewHolder.time().setText(Joiner.on(" · ").join(timeInfo));
    }

    public static @DrawableRes Integer getMessageStatusAsDrawable(
            final Message message, final int status) {
        final var transferable = message.getTransferable();
        return switch (status) {
            case Message.STATUS_WAITING -> R.drawable.ic_more_horiz_24dp;
            case Message.STATUS_UNSEND -> transferable == null ? null : R.drawable.ic_upload_24dp;
            case Message.STATUS_SEND -> R.drawable.ic_done_24dp;
            case Message.STATUS_SEND_RECEIVED, Message.STATUS_SEND_DISPLAYED ->
                    R.drawable.ic_done_all_24dp;
            case Message.STATUS_SEND_FAILED -> {
                final String errorMessage = message.getErrorMessage();
                if (Message.ERROR_MESSAGE_CANCELLED.equals(errorMessage)) {
                    yield R.drawable.ic_cancel_24dp;
                } else {
                    yield R.drawable.ic_error_24dp;
                }
            }
            case Message.STATUS_OFFERED -> R.drawable.ic_p2p_24dp;
            default -> null;
        };
    }

    @Nullable
    private String getAdditionalStatusInfo(final Message message, final int mergedStatus) {
        final String additionalStatusInfo;
        if (mergedStatus == Message.STATUS_SEND_FAILED) {
            final String errorMessage = Strings.nullToEmpty(message.getErrorMessage());
            final String[] errorParts = errorMessage.split("\\u001f", 2);
            if (errorParts.length == 2 && errorParts[0].equals("file-too-large")) {
                additionalStatusInfo = getContext().getString(R.string.file_too_large);
            } else {
                additionalStatusInfo = null;
            }
        } else if (mergedStatus == Message.STATUS_UNSEND) {
            final var transferable = message.getTransferable();
            if (transferable == null) {
                return null;
            }
            return getContext().getString(R.string.sending_file, transferable.getProgress());
        } else {
            additionalStatusInfo = null;
        }
        return additionalStatusInfo;
    }

    private void displayInfoMessage(
            BubbleMessageItemViewHolder viewHolder,
            CharSequence text,
            final BubbleColor bubbleColor) {
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.messageBody().setTypeface(null, Typeface.ITALIC);
        viewHolder.messageBody().setVisibility(View.VISIBLE);
        viewHolder.messageBox().setBackgroundTintMode(PorterDuff.Mode.SRC);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        viewHolder.messageBody().setText(text);
        viewHolder
                .messageBody()
                .setTextColor(bubbleToOnSurfaceVariant(viewHolder.messageBody(), bubbleColor));
        viewHolder.messageBody().setTextIsSelectable(false);
    }

    private void displayEmojiMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.downloadButton().setVisibility(GONE);
        viewHolder.audioPlayer().setVisibility(GONE);
        viewHolder.image().setVisibility(GONE);
        viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBody().setVisibility(View.VISIBLE);
        setTextColor(viewHolder.messageBody(), bubbleColor);
        viewHolder.messageBox().setBackgroundTintMode(PorterDuff.Mode.CLEAR);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        final var body = getSpannableBody(message);
        ImageSpan[] imageSpans = body.getSpans(0, body.length(), ImageSpan.class);
        float size = imageSpans.length == 1 || Emoticons.isEmoji(body.toString()) ? 5.0f : 2.0f;
        body.setSpan(
                new RelativeSizeSpan(size), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        viewHolder.messageBody().setText(body);
    }

    private void applyQuoteSpan(
            final TextView textView,
            Editable body,
            int start,
            int end,
            final BubbleColor bubbleColor,
            final boolean makeEdits) {
        if (makeEdits && start > 1 && !"\n\n".equals(body.subSequence(start - 2, start).toString())) {
            body.insert(start++, "\n");
            body.setSpan(
                    new DividerSpan(false), start - 2, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            end++;
        }
        if (makeEdits && end < body.length() - 1 && !"\n\n".equals(body.subSequence(end, end + 2).toString())) {
            body.insert(end, "\n");
            body.setSpan(
                    new DividerSpan(false),
                    end,
                    end + ("\n".equals(body.subSequence(end + 1, end + 2).toString()) ? 2 : 1),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        final DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        body.setSpan(
                new QuoteSpan(bubbleToOnSurfaceVariant(textView, bubbleColor), metrics),
                start,
                end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    public void handleTextQuotes(final TextView textView, final Editable body) {
        handleTextQuotes(textView, body, true);
    }

    public void handleTextQuotes(final TextView textView, final Editable body, final boolean deleteMarkers) {
        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        final BubbleColor bubbleColor = colorfulBackground ? (deleteMarkers ? BubbleColor.SECONDARY : BubbleColor.TERTIARY) : BubbleColor.SURFACE;
        handleTextQuotes(textView, body, bubbleColor, deleteMarkers);
    }

    /**
     * Applies QuoteSpan to group of lines which starts with > or » characters. Appends likebreaks
     * and applies DividerSpan to them to show a padding between quote and text.
     */
    public boolean handleTextQuotes(
            final TextView textView,
            final Editable body,
            final BubbleColor bubbleColor,
            final boolean deleteMarkers) {
        boolean startsWithQuote = false;
        int quoteDepth = 1;
        while (QuoteHelper.bodyContainsQuoteStart(body) && quoteDepth <= Config.QUOTE_MAX_DEPTH) {
            char previous = '\n';
            int lineStart = -1;
            int lineTextStart = -1;
            int quoteStart = -1;
            int skipped = 0;
            for (int i = 0; i <= body.length(); i++) {
                if (!deleteMarkers && QuoteHelper.isRelativeSizeSpanned(body, i)) {
                    skipped++;
                    continue;
                }
                char current = body.length() > i ? body.charAt(i) : '\n';
                if (lineStart == -1) {
                    if (previous == '\n') {
                        if (i < body.length() && QuoteHelper.isPositionQuoteStart(body, i)) {
                            // Line start with quote
                            lineStart = i;
                            if (quoteStart == -1) quoteStart = i - skipped;
                            if (i == 0) startsWithQuote = true;
                        } else if (quoteStart >= 0) {
                            // Line start without quote, apply spans there
                            applyQuoteSpan(textView, body, quoteStart, i - 1, bubbleColor, deleteMarkers);
                            quoteStart = -1;
                        }
                    }
                } else {
                    // Remove extra spaces between > and first character in the line
                    // > character will be removed too
                    if (current != ' ' && lineTextStart == -1) {
                        lineTextStart = i;
                    }
                    if (current == '\n') {
                        if (deleteMarkers) {
                            i -= lineTextStart - lineStart;
                            body.delete(lineStart, lineTextStart);
                            if (i == lineStart) {
                                // Avoid empty lines because span over empty line can be hidden
                                body.insert(i++, " ");
                            }
                        } else {
                            body.setSpan(new RelativeSizeSpan(i - (lineTextStart - lineStart) == lineStart ? 1 : 0), lineStart, lineTextStart, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE | StylingHelper.XHTML_REMOVE << Spanned.SPAN_USER_SHIFT);
                        }
                        lineStart = -1;
                        lineTextStart = -1;
                    }
                }
                previous = current;
                skipped = 0;
            }
            if (quoteStart >= 0) {
                // Apply spans to finishing open quote
                applyQuoteSpan(textView, body, quoteStart, body.length(), bubbleColor, deleteMarkers);
            }
            quoteDepth++;
        }
        return startsWithQuote;
    }

    private SpannableStringBuilder getSpannableBody(final Message message) {
        Drawable fallbackImg = ResourcesCompat.getDrawable(activity.getResources(), R.drawable.ic_photo_24dp, null);
        return message.getSpannableBody(new Thumbnailer(message), fallbackImg);
    }

    private void displayTextMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        viewHolder.inReplyToQuote().setVisibility(GONE);
        viewHolder.downloadButton().setVisibility(GONE);
        viewHolder.image().setVisibility(GONE);
        viewHolder.audioPlayer().setVisibility(GONE);
        viewHolder.messageBody().setVisibility(View.VISIBLE);
        setTextColor(viewHolder.messageBody(), bubbleColor);
        setTextSize(viewHolder.messageBody(), this.bubbleDesign.largeFont);
        viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
        viewHolder.messageBox().setBackgroundTintMode(PorterDuff.Mode.SRC);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }

        final ViewGroup.LayoutParams layoutParams = viewHolder.messageBody().getLayoutParams();
        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewHolder.messageBody().setLayoutParams(layoutParams);
        if (appSettings.isLargeFont()) {
            viewHolder.inReplyToQuote().setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            viewHolder.inReplyToQuote().setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        } else {
            viewHolder.inReplyToQuote().setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        }
        final ViewGroup.LayoutParams qlayoutParams = viewHolder.inReplyToQuote().getLayoutParams();
        qlayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        viewHolder.inReplyToQuote().setLayoutParams(qlayoutParams);

        final var rawBody = message.getBody();
        if (Strings.isNullOrEmpty(rawBody)) {
            viewHolder.messageBody().setText("");
            viewHolder.messageBody().setTextIsSelectable(false);
            toggleWhisperInfo(viewHolder, message, bubbleColor);
            return;
        }
        viewHolder.messageBody().setTextIsSelectable(true);
        final String nick = UIHelper.getMessageDisplayName(message);
        SpannableStringBuilder body = getSpannableBody(message);
        final var processMarkup = body.getSpans(0, body.length(), Message.PlainTextSpan.class).length > 0;
        if (body.length() > Config.MAX_DISPLAY_MESSAGE_CHARS) {
            body = new SpannableStringBuilder(body, 0, Config.MAX_DISPLAY_MESSAGE_CHARS);
            body.append("…");
        }
        if (processMarkup)
            StylingHelper.format(body, viewHolder.messageBody().getCurrentTextColor());
        MyLinkify.addLinks(body, message.getConversation().getAccount(), message.getConversation().getJid(), activity.xmppConnectionService);
        boolean startsWithQuote = processMarkup && handleTextQuotes(viewHolder.messageBody(), body, bubbleColor, true);
        for (final android.text.style.QuoteSpan quote : body.getSpans(0, body.length(), android.text.style.QuoteSpan.class)) {
            int start = body.getSpanStart(quote);
            int end = body.getSpanEnd(quote);
            if (start < 0 || end < 0) continue;

            body.removeSpan(quote);
            applyQuoteSpan(viewHolder.messageBody(), body, start, end, bubbleColor, true);
            if (start == 0) {
                if (message.getInReplyTo() == null) {
                    startsWithQuote = true;
                } else {
                    viewHolder.inReplyToQuote().setText(body.subSequence(start, end));
                    viewHolder.inReplyToQuote().setVisibility(View.VISIBLE);
                    body.delete(start, end);
                    while (body.length() > start && body.charAt(start) == '\n')
                        body.delete(start, 1); // Newlines after quote
                }
            }
        }
        boolean hasMeCommand = body.toString().startsWith(Message.ME_COMMAND);
        if (hasMeCommand) {
            body.replace(0, Message.ME_COMMAND.length(), String.format("%s ", nick));
        }
        if (!message.isPrivateMessage()) {
            if (hasMeCommand && body.length() > nick.length()) {
                body.setSpan(
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        0,
                        nick.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        } else {
            String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker =
                        activity.getString(
                                R.string.private_message_to,
                                Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            body.insert(0, privateMarker);
            int privateMarkerIndex = privateMarker.length();
            if (startsWithQuote) {
                body.insert(privateMarkerIndex, "\n\n");
                body.setSpan(
                        new DividerSpan(false),
                        privateMarkerIndex,
                        privateMarkerIndex + 2,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                body.insert(privateMarkerIndex, " ");
            }
            body.setSpan(
                    new ForegroundColorSpan(
                            bubbleToOnSurfaceVariant(viewHolder.messageBody(), bubbleColor)),
                    0,
                    privateMarkerIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    privateMarkerIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (hasMeCommand) {
                body.setSpan(
                        new StyleSpan(Typeface.BOLD_ITALIC),
                        privateMarkerIndex + 1,
                        privateMarkerIndex + 1 + nick.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if (message.getConversation().getMode() == Conversation.MODE_MULTI
                && message.getStatus() == Message.STATUS_RECEIVED) {
            if (message.getConversation() instanceof Conversation conversation) {
                Pattern pattern =
                        NotificationService.generateNickHighlightPattern(
                                conversation.getMucOptions().getActualNick());
                Matcher matcher = pattern.matcher(body);
                while (matcher.find()) {
                    body.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            matcher.start(),
                            matcher.end(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        for (final var emoji : EmojiManager.extractEmojisInOrderWithIndex(body.toString())) {
            var end = emoji.getCharIndex() + emoji.getEmoji().getEmoji().length();
            if (body.length() > end && body.charAt(end) == '\uFE0F') end++;
            body.setSpan(
                    new RelativeSizeSpan(1.2f),
                    emoji.getCharIndex(),
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        // Make custom emoji bigger too, to match emoji
        for (final var span : body.getSpans(0, body.length(), InlineImageSpan.class)) {
            body.setSpan(
                    new RelativeSizeSpan(2.0f),
                    body.getSpanStart(span),
                    body.getSpanEnd(span),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        if (highlightedTerm != null) {
            StylingHelper.highlight(viewHolder.messageBody(), body, highlightedTerm);
        }

        viewHolder.messageBody().setAutoLinkMask(0);
        viewHolder.messageBody().setText(body);

        // Experimental expandable text, collapse after 8 lines
        if (activity.xmppConnectionService.getBooleanPreference("set_text_collapsable", R.bool.set_text_collapsable)) {
            viewHolder.messageBody().post(() -> {
                int lineCount = viewHolder.messageBody().getLineCount();
                if (lineCount > 8) {
                    viewHolder.showMore().setVisibility(View.VISIBLE);
                } else {
                    viewHolder.showMore().setVisibility(GONE);
                }
            });
            final boolean[] isTextViewClicked = {false};
            viewHolder.showMore().setOnClickListener(v -> {
                if (isTextViewClicked[0]) {
                    //This will shrink textview to 8 lines if it is expanded.
                    viewHolder.showMore().setText(R.string.show_more);
                    viewHolder.messageBody().setMaxLines(8);
                    isTextViewClicked[0] = false;
                } else {
                    //This will expand the textview if it is of 8 lines
                    viewHolder.showMore().setText(R.string.show_less);
                    viewHolder.messageBody().setMaxLines(Integer.MAX_VALUE);
                    isTextViewClicked[0] = true;
                }
            });
        } else viewHolder.messageBody().setMaxLines(Integer.MAX_VALUE);

        if (body.length() <= 0) viewHolder.messageBody().setVisibility(GONE);
        BetterLinkMovementMethod method = getBetterLinkMovementMethod();
        viewHolder.messageBody().setMovementMethod(method);
    }

    @NonNull
    private BetterLinkMovementMethod getBetterLinkMovementMethod() {
        BetterLinkMovementMethod method = new BetterLinkMovementMethod() {
            @Override
            protected void dispatchUrlLongClick(TextView tv, ClickableSpan span) {
                if (span instanceof URLSpan || mOnInlineImageLongClickedListener == null) {
                    tv.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
                    super.dispatchUrlLongClick(tv, span);
                    return;
                }

                Spannable body = (Spannable) tv.getText();
                ImageSpan[] imageSpans = body.getSpans(body.getSpanStart(span), body.getSpanEnd(span), ImageSpan.class);
                if (imageSpans.length > 0) {
                    Uri uri = Uri.parse(imageSpans[0].getSource());
                    Cid cid = BobTransfer.cid(uri);
                    if (cid == null) return;
                    if (mOnInlineImageLongClickedListener.onInlineImageLongClicked(cid)) {
                        tv.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
                    }
                }
            }
        };
        method.setOnLinkLongClickListener((tv, url) -> {
            tv.dispatchTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0));
            ShareUtil.copyLinkToClipboard(activity, url);
            return true;
        });
        return  method;
    }

    private void displayDownloadableMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final String text,
            final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(GONE);
        List<Element> thumbs = message.getFileParams() != null ? message.getFileParams().getThumbnails() : null;
        if (thumbs != null && !thumbs.isEmpty()) {
            for (Element thumb : thumbs) {
                Uri uri = Uri.parse(thumb.getAttribute("uri"));
                if (Objects.equals(uri.getScheme(), "data")) {
                    String[] parts = uri.getSchemeSpecificPart().split(",", 2);
                    parts = parts[0].split(";");
                    if (!parts[0].equals("image/blurhash") && !parts[0].equals("image/thumbhash") && !parts[0].equals("image/jpeg") && !parts[0].equals("image/png") && !parts[0].equals("image/webp") && !parts[0].equals("image/gif"))
                        continue;
                } else if (Objects.equals(uri.getScheme(), "cid")) {
                    Cid cid = BobTransfer.cid(uri);
                    if (cid == null) continue;
                    DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                    if (f == null || !f.canRead()) {
                        if (!message.trusted() && !message.getConversation().canInferPresence())
                            continue;

                        try {
                            new BobTransfer(BobTransfer.uri(cid), message.getConversation().getAccount(), message.getCounterpart(), activity.xmppConnectionService).start();
                        } catch (final NoSuchAlgorithmException | URISyntaxException ignored) {
                        }
                        continue;
                    }
                } else {
                    continue;
                }

                int width = message.getFileParams().width;
                if (width < 1 && thumb.getAttribute("width") != null)
                    width = Integer.parseInt(thumb.getAttribute("width"));
                if (width < 1) width = 1920;

                int height = message.getFileParams().height;
                if (height < 1 && thumb.getAttribute("height") != null)
                    height = Integer.parseInt(thumb.getAttribute("height"));
                if (height < 1) height = 1080;

                viewHolder.image().setVisibility(View.VISIBLE);
                imagePreviewLayout(width, height, viewHolder.image(), message.getInReplyTo() != null, true, viewHolder);
                activity.loadBitmap(message, viewHolder.image());
                viewHolder.image().setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));

                break;
            }
        }
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        viewHolder.audioPlayer().setVisibility(GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder.downloadButton().setText(text);
        final var attachment = Attachment.of(message);
        final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
        viewHolder.downloadButton().setIconResource(imageResource);
        viewHolder
                .downloadButton()
                .setOnClickListener(v -> ConversationFragment.downloadFile(activity, message));
    }

    private void displayWebxdcMessage(BubbleMessageItemViewHolder viewHolder, final Message message, final BubbleColor bubbleColor) {
        Cid webxdcCid = message.getFileParams().getCids().get(0);
        WebxdcPage webxdc = new WebxdcPage(activity, webxdcCid, message);
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(GONE);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        viewHolder.audioPlayer().setVisibility(GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder.downloadButton().setIconResource(0);
        viewHolder.downloadButton().setText(activity.getString(R.string.open) + " " + webxdc.getName());
        viewHolder.downloadButton().setOnClickListener(v -> {
            Conversation conversation = (Conversation) message.getConversation();
            if (!conversation.switchToSession("webxdc\0" + message.getUuid())) {
                conversation.startWebxdc(webxdc);
            }
        });
        viewHolder.image().setOnClickListener(v -> {
            Conversation conversation = (Conversation) message.getConversation();
            if (!conversation.switchToSession("webxdc\0" + message.getUuid())) {
                conversation.startWebxdc(webxdc);
            }
        });

        final WebxdcUpdate lastUpdate;
        synchronized (lastWebxdcUpdate) {
            lastUpdate = lastWebxdcUpdate.get(message.getUuid());
        }
        if (lastUpdate == null) {
            new Thread(() -> {
                final WebxdcUpdate update = activity.xmppConnectionService.findLastWebxdcUpdate(message);
                if (update != null) {
                    synchronized (lastWebxdcUpdate) {
                        lastWebxdcUpdate.put(message.getUuid(), update);
                    }
                    activity.xmppConnectionService.updateConversationUi();
                }
            }).start();
        } else {
            if (lastUpdate.getSummary() != null || lastUpdate.getDocument() != null) {
                viewHolder.messageBody().setVisibility(View.VISIBLE);
                viewHolder.messageBody().setText(
                        (lastUpdate.getDocument() == null ? "" : lastUpdate.getDocument() + "\n") +
                                (lastUpdate.getSummary() == null ? "" : lastUpdate.getSummary())
                );
            }
        }

        final LruCache<String, Drawable> cache = activity.xmppConnectionService.getDrawableCache();
        final Drawable d = cache.get("webxdc:icon:" + webxdcCid);
        if (d == null) {
            new Thread(() -> {
                Drawable icon = webxdc.getIcon();
                if (icon != null) {
                    cache.put("webxdc:icon:" + webxdcCid, icon);
                    activity.xmppConnectionService.updateConversationUi();
                }
            }).start();
        } else {
            viewHolder.image().setVisibility(View.VISIBLE);
            viewHolder.image().setImageDrawable(d);
            imagePreviewLayout(d.getIntrinsicWidth(), d.getIntrinsicHeight(), viewHolder.image(), message.getInReplyTo() != null, true, viewHolder);
        }
    }

    private void displayOpenableMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(GONE);
        viewHolder.audioPlayer().setVisibility(GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        viewHolder
                .downloadButton()
                .setText(
                        activity.getString(
                                R.string.open_x_file,
                                UIHelper.getFileDescriptionString(activity, message)));
        final var attachment = Attachment.of(message);
        final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
        viewHolder.downloadButton().setIconResource(imageResource);
        viewHolder.downloadButton().setOnClickListener(v -> openDownloadable(message));
    }

    private void displayURIMessage(
            BubbleMessageItemViewHolder viewHolder, final Message message, final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.messageBody().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.downloadButton().setVisibility(View.VISIBLE);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        final var uri = message.wholeIsKnownURI();
        if ("bitcoin".equals(uri.getScheme())) {
            final var amount = uri.getQueryParameter("amount");
            final var formattedAmount = amount == null || amount.isEmpty() ? "" : amount + " ";
            viewHolder.downloadButton().setIconResource(R.drawable.bitcoin_24dp);
            viewHolder.downloadButton().setText("Send " + formattedAmount + "Bitcoin");
        } else if ("bitcoincash".equals(uri.getScheme())) {
            final var amount = uri.getQueryParameter("amount");
            final var formattedAmount = amount == null || amount.isEmpty() ? "" : amount + " ";
            viewHolder.downloadButton().setIconResource(R.drawable.bitcoin_cash_24dp);
            viewHolder.downloadButton().setText("Send " + formattedAmount + "Bitcoin Cash");
        } else if ("ethereum".equals(uri.getScheme())) {
            final var amount = uri.getQueryParameter("value");
            final var formattedAmount = amount == null || amount.isEmpty() ? "" : amount + " ";
            viewHolder.downloadButton().setIconResource(R.drawable.eth_24dp);
            viewHolder.downloadButton().setText("Send " + formattedAmount + "via Ethereum");
        } else if ("monero".equals(uri.getScheme())) {
            final var amount = uri.getQueryParameter("tx_amount");
            final var formattedAmount = amount == null || amount.isEmpty() ? "" : amount + " ";
            viewHolder.downloadButton().setIconResource(R.drawable.monero_24dp);
            viewHolder.downloadButton().setText("Send " + formattedAmount + "Monero");
        } else if ("wownero".equals(uri.getScheme())) {
            final var amount = uri.getQueryParameter("tx_amount");
            final var formattedAmount = amount == null || amount.isEmpty() ? "" : amount + " ";
            viewHolder.downloadButton().setIconResource(R.drawable.wownero_24dp);
            viewHolder.downloadButton().setText("Send " + formattedAmount + "Wownero");
        } else if ("taler".equals(uri.getScheme())) {
            final var amount = uri.getQueryParameter("amount");
            final var formattedAmount = amount == null || amount.isEmpty() ? "" : amount + " ";
            viewHolder.downloadButton().setIconResource(R.drawable.taler_icon_24dp);
            viewHolder.downloadButton().setText("Send " + formattedAmount + "Taler");
        }
        viewHolder.downloadButton().setOnClickListener(v -> new FixedURLSpan(message.getRawBody()).onClick(v));
    }

    private void displayLocationMessage(
            final BubbleMessageItemViewHolder viewHolder, final Message message, final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        final String url = GeoHelper.MapPreviewUri(message, activity);
        viewHolder.audioPlayer().setVisibility(GONE);
        if (message.isGeoUri() && viewHolder.messageBody().getVisibility() == GONE) {
            viewHolder.messageBox().setBackgroundTintMode(PorterDuff.Mode.CLEAR);
            viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
            viewHolder.inReplyToBox().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.inReplyToBox().setBackgroundTintList(bubbleToColorStateList(viewHolder.inReplyToBox(), bubbleColor));
            viewHolder.inReplyToQuote().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_surface_container));
            viewHolder.inReplyToQuote().setBackgroundTintList(bubbleToColorStateList(viewHolder.inReplyToQuote(), bubbleColor));
            if (viewHolder.username() != null) {
                viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
                viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
            }
        }
        if (activity.xmppConnectionService != null && activity.xmppConnectionService.getBooleanPreference("show_maps_inside", R.bool.show_maps_inside)) {
            Glide.with(activity)
                    .load(Uri.parse(url))
                    .placeholder(R.drawable.marker)
                    .error(R.drawable.marker)
                    .into(viewHolder.image());
            viewHolder.image().setVisibility(View.VISIBLE);
            imagePreviewLayout(540, 540, viewHolder.image(), message.getInReplyTo() != null, true, viewHolder);
            viewHolder.image().setOnClickListener(v -> showLocation(message));
            viewHolder.downloadButton().setVisibility(GONE);
        } else {
            viewHolder.image().setVisibility(GONE);
            viewHolder.downloadButton().setVisibility(View.VISIBLE);
            viewHolder.downloadButton().setText(R.string.show_location);
            final var attachment = Attachment.of(message);
            final @DrawableRes int imageResource = MediaAdapter.getImageDrawable(attachment);
            viewHolder.downloadButton().setIconResource(imageResource);
            viewHolder.downloadButton().setOnClickListener(v -> showLocation(message));
        }
    }

    private void displayAudioMessage(
            final BubbleMessageItemViewHolder viewHolder,
            Message message,
            final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.image().setVisibility(View.GONE);
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
        viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
        if (viewHolder.username() != null) {
            viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.username(), bubbleColor));
        }
        final RelativeLayout audioPlayer = viewHolder.audioPlayer();
        audioPlayer.setVisibility(View.VISIBLE);
        AudioPlayer.ViewHolder.get(audioPlayer).setBubbleColor(bubbleColor);
        this.audioPlayer.init(audioPlayer, message);
    }

    private void displayMediaPreviewMessage(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        displayTextMessage(viewHolder, message, bubbleColor);
        viewHolder.downloadButton().setVisibility(View.GONE);
        viewHolder.audioPlayer().setVisibility(View.GONE);
        viewHolder.image().setVisibility(View.VISIBLE);
        if (message.isFileOrImage() && viewHolder.messageBody().getVisibility() == GONE) {
            viewHolder.messageBox().setBackgroundTintMode(PorterDuff.Mode.CLEAR);
            viewHolder.statusLine().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.statusLine().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
            viewHolder.inReplyToBox().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
            viewHolder.inReplyToBox().setBackgroundTintList(bubbleToColorStateList(viewHolder.inReplyToBox(), bubbleColor));
            viewHolder.inReplyToQuote().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_surface_container));
            viewHolder.inReplyToQuote().setBackgroundTintList(bubbleToColorStateList(viewHolder.inReplyToQuote(), bubbleColor));
            if (viewHolder.username() != null) {
                viewHolder.username().setBackground(ContextCompat.getDrawable(activity, R.drawable.background_message_bubble));
                viewHolder.username().setBackgroundTintList(bubbleToColorStateList(viewHolder.statusLine(), bubbleColor));
            }
        }
        final FileParams params = message.getFileParams();
        imagePreviewLayout(params.width, params.height, viewHolder.image(), message.getInReplyTo() != null, viewHolder.messageBody().getVisibility() != GONE, viewHolder);
        activity.loadBitmap(message, viewHolder.image());
        viewHolder.image().setOnClickListener(v -> openDownloadable(message));
    }

    private void imagePreviewLayout(int w, int h, ShapeableImageView image, boolean otherAbove, boolean otherBelow, BubbleMessageItemViewHolder viewHolder) {
        // metrics.density is used multiple times, cache it locally or make it a member if metrics is stable.
        // Assuming 'density' is now a member variable 'this.density' initialized in constructor.

        final int scaledW;
        final int scaledH;

        // Use the pre-fetched imagePreviewWidthTarget
        if (Math.max(h, w) * this.density <= this.imagePreviewWidthTarget) {
            scaledW = (int) (w * this.density);
            scaledH = (int) (h * this.density);
        } else if (Math.max(h, w) <= this.imagePreviewWidthTarget) {
            scaledW = w;
            scaledH = h;
        } else if (w <= h) {
            scaledW = (int) (w / ((double) h / this.imagePreviewWidthTarget));
            scaledH = (int) this.imagePreviewWidthTarget;
        } else {
            scaledW = (int) this.imagePreviewWidthTarget;
            scaledH = (int) (h / ((double) w / this.imagePreviewWidthTarget));
        }

        final var bodyWidth = Math.max(viewHolder.messageBody().getWidth(), viewHolder.downloadButton().getWidth() + (int)this.padding22dp); // Use pre-calculated padding22dp

        // Use pre-calculated thresholds
        float currentTargetImageWidth = this.targetImageWidthLargeThreshold;
        if (!otherBelow) {
            currentTargetImageWidth = this.targetImageWidthSmallThreshold;
        }

        if (bodyWidth > 0 && bodyWidth < currentTargetImageWidth) {
            currentTargetImageWidth = bodyWidth;
        }

        final boolean small = scaledW < currentTargetImageWidth;

        ViewGroup.LayoutParams currentParams = image.getLayoutParams();
        if (currentParams instanceof LinearLayout.LayoutParams linearParams) {
            if (linearParams.width != scaledW || linearParams.height != scaledH) {
                linearParams.width = scaledW;
                linearParams.height = scaledH;
                image.setLayoutParams(linearParams); // Only set if changed
            }
        } else {
            // Fallback or if it's a different type of LayoutParams initially
            image.setLayoutParams(new LinearLayout.LayoutParams(scaledW, scaledH));
        }


        // --- Start of Simplified Corner Rounding ---
        ShapeAppearanceModel.Builder shapeBuilder = new ShapeAppearanceModel.Builder();

        // Set all corners to be rounded with imageRadiusDim (or use bubbleRadiusDim if preferred)
        shapeBuilder = shapeBuilder.setAllCorners(CornerFamily.ROUNDED, this.imageRadiusDim);

        image.setShapeAppearanceModel(shapeBuilder.build());
        // --- End of Simplified Corner Rounding ---


        // Adjust padding based on the 'small' flag or other criteria if needed.
        // If you always want the same padding, you can set it unconditionally.
        if (small) { // Or some other condition you define for padding
            image.setPadding(0, (int) this.padding8dp, 0, 0);
        } else {
            image.setPadding(0, 0, 0, 0);
        }


        // The rest of the logic for adjusting messageBody and inReplyToQuote widths
        // can remain if it's still relevant to your layout when an image is present.
        // However, if the image corners are always fully rounded, the visual interaction
        // with these elements might change, so review if this is still needed as is.
        if (!small) { // This condition might also need re-evaluation.
            // For example, if you want these width adjustments to always happen
            // when an image is present, regardless of 'small'.
            final ViewGroup.LayoutParams bodyLayoutParams = viewHolder.messageBody().getLayoutParams();
            int targetWidth = (int) (scaledW - this.padding22dp);

            if (bodyLayoutParams.width != targetWidth) {
                bodyLayoutParams.width = targetWidth;
                viewHolder.messageBody().setLayoutParams(bodyLayoutParams);
            }

            if (viewHolder.inReplyToQuote().getVisibility() == View.VISIBLE) {
                final ViewGroup.LayoutParams qLayoutParams = viewHolder.inReplyToQuote().getLayoutParams();
                if (qLayoutParams.width != targetWidth) {
                    qLayoutParams.width = targetWidth;
                    viewHolder.inReplyToQuote().setLayoutParams(qLayoutParams);
                }
            }
        }
    }

    private void toggleWhisperInfo(
            final BubbleMessageItemViewHolder viewHolder,
            final Message message,
            final BubbleColor bubbleColor) {
        if (message.isPrivateMessage()) {
            final String privateMarker;
            if (message.getStatus() <= Message.STATUS_RECEIVED) {
                privateMarker = activity.getString(R.string.private_message);
            } else {
                Jid cp = message.getCounterpart();
                privateMarker =
                        activity.getString(
                                R.string.private_message_to,
                                Strings.nullToEmpty(cp == null ? null : cp.getResource()));
            }
            final SpannableString body = new SpannableString(privateMarker);
            body.setSpan(
                    new ForegroundColorSpan(
                            bubbleToOnSurfaceVariant(viewHolder.messageBody(), bubbleColor)),
                    0,
                    privateMarker.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            body.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    privateMarker.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            viewHolder.messageBody().setText(body);
            viewHolder.messageBody().setTypeface(null, Typeface.NORMAL);
            viewHolder.messageBody().setVisibility(View.VISIBLE);
        } else {
            viewHolder.messageBody().setVisibility(GONE);
        }
    }

    private void loadMoreMessages(final Conversation conversation) {
        conversation.setLastClearHistory(0, null);
        activity.runOnUiThread(() -> activity.xmppConnectionService.updateConversation(conversation));
        conversation.setHasMessagesLeftOnServer(true);
        conversation.setFirstMamReference(null);
        long timestamp = conversation.getLastMessageTransmitted().getTimestamp();
        if (timestamp == 0) {
            timestamp = System.currentTimeMillis();
        }
        conversation.messagesLoaded.set(true);
        MessageArchiveService.Query query =
                activity.xmppConnectionService
                        .getMessageArchiveService()
                        .query(conversation, new MamReference(0), timestamp, false);
        if (query != null) {
            Toast.makeText(activity, R.string.fetching_history_from_server, Toast.LENGTH_LONG)
                    .show();
        } else {
            Toast.makeText(
                            activity,
                            R.string.not_fetching_history_retention_period,
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private MessageItemViewHolder getViewHolder(
            final View view, final @NonNull ViewGroup parent, final int type) {
        if (view != null && view.getTag() instanceof MessageItemViewHolder messageItemViewHolder) {
            return messageItemViewHolder;
        } else {
            final MessageItemViewHolder viewHolder =
                switch (type) {
                    case RTP_SESSION ->
                            new RtpSessionMessageItemViewHolder(
                                    DataBindingUtil.inflate(
                                            LayoutInflater.from(parent.getContext()),
                                            R.layout.item_message_rtp_session,
                                            parent,
                                            false));
                    case DATE_SEPARATOR ->
                            new DateSeperatorMessageItemViewHolder(
                                    DataBindingUtil.inflate(
                                            LayoutInflater.from(parent.getContext()),
                                            R.layout.item_message_date_bubble,
                                            parent,
                                            false));
                    case STATUS ->
                            new StatusMessageItemViewHolder(
                                    DataBindingUtil.inflate(
                                            LayoutInflater.from(parent.getContext()),
                                            R.layout.item_message_status,
                                            parent,
                                            false));
                    // Ensure END and START produce distinct types if they have different render logic
                    // or are checked separately in getView's switch.
                    case END ->
                            new EndBubbleMessageItemViewHolder( // Make sure this is a distinct class
                                    DataBindingUtil.inflate(
                                            LayoutInflater.from(parent.getContext()),
                                            R.layout.item_message_end,
                                            parent,
                                            false));
                    case START ->
                            new StartBubbleMessageItemViewHolder( // Make sure this is a distinct class
                                    DataBindingUtil.inflate(
                                            LayoutInflater.from(parent.getContext()),
                                            R.layout.item_message_start,
                                            parent,
                                            false));
                    default -> {
                        Log.e("MessageAdapter", "Unable to create ViewHolder for type: " + type);
                        throw new AssertionError("Unable to create ViewHolder for type: " + type);
                    }
                };
            viewHolder.itemView.setTag(viewHolder);
            return viewHolder;
        }
    }

    @NonNull
    @Override
    public View getView(final int position, final View view, final @NonNull ViewGroup parent) {
        final Message message = getItem(position);
        final int type;
        if (message != null) {
            type = getItemViewType(message, bubbleDesign.alignStart);

            final MessageItemViewHolder viewHolder = getViewHolder(view, parent, type);

            if (type == DATE_SEPARATOR
                    && viewHolder instanceof DateSeperatorMessageItemViewHolder messageItemViewHolder) {
                return render(message, messageItemViewHolder);
            }

            if (type == RTP_SESSION
                    && viewHolder instanceof RtpSessionMessageItemViewHolder messageItemViewHolder) {
                return render(message, messageItemViewHolder);
            }

            if (type == STATUS
                    && viewHolder instanceof StatusMessageItemViewHolder messageItemViewHolder) {
                return render(message, messageItemViewHolder);
            }

            if ((type == END || type == START)
                    && viewHolder instanceof BubbleMessageItemViewHolder messageItemViewHolder) {
                return render(position, message, messageItemViewHolder);
            }
        }
        throw new AssertionError();
    }

    private View render(
            final int position,
            final Message message,
            final BubbleMessageItemViewHolder viewHolder) {
        final boolean omemoEncryption = message.getEncryption() == Message.ENCRYPTION_AXOLOTL;
        final boolean isInValidSession =
                message.isValidInSession() && (!omemoEncryption || message.isTrusted());
        final Conversational conversation = message.getConversation();
        final Account account = conversation.getAccount();
        final List<Element> commands = message.getCommands();

        viewHolder.linkDescriptions().setOnItemClickListener((adapter, v, pos, id) -> {
            final var desc = (Element) adapter.getItemAtPosition(pos);
            var url = desc.findChildContent("url", "https://ogp.me/ns#");
            // should we prefer about? Maybe, it's the real original link, but it's not what we show the user
            if (url == null || url.isEmpty())
                url = desc.getAttribute("{http://www.w3.org/1999/02/22-rdf-syntax-ns#}about");
            if (url == null || url.isEmpty()) return;
            new FixedURLSpan(url).onClick(v);
        });

        if (viewHolder.messageBody() != null) {
            viewHolder.messageBody().setCustomSelectionActionModeCallback(new MessageTextActionModeCallback(this, viewHolder.messageBody()));
        }

        if (viewHolder.time() != null) {
            if (message.isAttention()) {
                viewHolder.time().setTypeface(null, Typeface.BOLD);
            } else {
                viewHolder.time().setTypeface(null, Typeface.NORMAL);
            }
        }

        final var black = MaterialColors.getColor(viewHolder.root(), com.google.android.material.R.attr.colorSecondaryContainer) == viewHolder.root().getContext().getColor(android.R.color.black);
        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        final boolean received = message.getStatus() == Message.STATUS_RECEIVED;
        final BubbleColor bubbleColor;
        if (received) {
            if (isInValidSession) {
                bubbleColor = colorfulBackground || black ? BubbleColor.SECONDARY : BubbleColor.SURFACE;
            } else {
                bubbleColor = BubbleColor.WARNING;
            }
        } else {
            if (!colorfulBackground && black) {
                bubbleColor = BubbleColor.SECONDARY;
            } else {
                bubbleColor = colorfulBackground ? BubbleColor.TERTIARY : BubbleColor.SURFACE_HIGH;
            }
        }

        if (viewHolder.threadIdenticon() != null) {
            viewHolder.threadIdenticon().setVisibility(GONE);
            final Element thread = message.getThread();
            if (thread != null) {
                final String threadId = thread.getContent();
                if (threadId != null) {
                    final var roles = MaterialColors.getColorRoles(activity, UIHelper.getColorForName(threadId));
                    viewHolder.threadIdenticon().setVisibility(View.VISIBLE);
                    viewHolder.threadIdenticon().setColor(roles.getAccent());
                    viewHolder.threadIdenticon().setHash(UIHelper.identiconHash(threadId));
                }
            }
        }

        final var mergeIntoTop = mergeIntoTop(position, message);
        final var mergeIntoBottom = mergeIntoBottom(position, message);
        final var showAvatar =
                bubbleDesign.showAvatars
                        || (viewHolder instanceof StartBubbleMessageItemViewHolder
                        && message.getConversation().getMode() == Conversation.MODE_MULTI);
        setBubblePadding(viewHolder.root(), mergeIntoTop, mergeIntoBottom);
        if (showAvatar) {
            final var requiresAvatar =
                    viewHolder instanceof StartBubbleMessageItemViewHolder
                            ? !mergeIntoTop
                            : !mergeIntoBottom;
            setRequiresAvatar(viewHolder, requiresAvatar, message);
            AvatarWorkerTask.loadAvatar(message, viewHolder.contactPicture(), R.dimen.avatar);
        } else {
            viewHolder.contactPicture().setVisibility(View.GONE);
        }
        setAvatarDistance(viewHolder.messageBox(), viewHolder.getClass(), showAvatar);
        viewHolder.messageBox().setClipToOutline(true); //remove to show tails

        resetClickListener(viewHolder.messageBox(), viewHolder.messageBody());

        viewHolder.messageBox().setOnClickListener(v -> {
            if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                MessageAdapter.this.mOnMessageBoxClickedListener
                        .onContactPictureClicked(message);
            }
        });


        // monocles swipe feature
        SwipeLayout swipeLayout = viewHolder.layoutSwipe();

        //set show mode.
        swipeLayout.setShowMode(SwipeLayout.ShowMode.PullOut);

        //add drag edge.(If the BottomView has 'layout_gravity' attribute, this line is unnecessary)
        swipeLayout.addDrag(SwipeLayout.DragEdge.Left, viewHolder.bottomWrapper());

        swipeLayout.addSwipeListener(new SwipeLayout.SwipeListener() {
            @Override
            public void onClose(SwipeLayout layout) {
                swipeLayout.refreshDrawableState();
                swipeLayout.clearAnimation();
                //when the SurfaceView totally cover the BottomView.
            }

            @Override
            public void onUpdate(SwipeLayout layout, int leftOffset, int topOffset) {
                swipeLayout.setClickToClose(true);
                //you are swiping.
            }

            @Override
            public void onStartOpen(SwipeLayout layout) {
                swipeLayout.setClickToClose(true);

            }

            @Override
            public void onOpen(SwipeLayout layout) {
                swipeLayout.refreshDrawableState();
                //when the BottomView totally show.
                if (mConversationFragment != null) {
                    mConversationFragment.quoteMessage(message);
                } else {
                    activity.switchToConversationAndQuote(wrap(message.getConversation()), MessageUtils.prepareQuote(message));
                }
                swipeLayout.close(true);
                swipeLayout.setClickToClose(true);
            }

            @Override
            public void onStartClose(SwipeLayout layout) {
                swipeLayout.close(true);
                swipeLayout.setClickToClose(true);
            }

            @Override
            public void onHandRelease(SwipeLayout layout, float xvel, float yvel) {
                swipeLayout.refreshDrawableState();
                swipeLayout.close(true);
            }
        });

        reactionsPopup(message, viewHolder);

        viewHolder.messageBody().setOnClickListener(v -> {
            if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                MessageAdapter.this.mOnMessageBoxClickedListener
                        .onContactPictureClicked(message);
            }
        });
        viewHolder.messageBody().setAccessibilityDelegate(null);

        viewHolder
                .contactPicture()
                .setOnClickListener(
                        v -> {
                            if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
                                MessageAdapter.this.mOnContactPictureClickedListener
                                        .onContactPictureClicked(message);
                            }
                        });
        viewHolder
                .contactPicture()
                .setOnLongClickListener(
                        v -> {
                            if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
                                MessageAdapter.this.mOnContactPictureLongClickedListener
                                        .onContactPictureLongClicked(v, message);
                                return true;
                            } else {
                                return false;
                            }
                        });

        boolean footerWrap = false;
        final Transferable transferable = message.getTransferable();
        final boolean unInitiatedButKnownSize = MessageUtils.unInitiatedButKnownSize(message);

        final boolean muted = message.getStatus() == Message.STATUS_RECEIVED && conversation.getMode() == Conversation.MODE_MULTI && activity.xmppConnectionService.isMucUserMuted(new MucOptions.User(null, conversation.getJid(), message.getOccupantId(), null, null));
        if (muted) {
            // Muted MUC participant
            displayInfoMessage(viewHolder, "Muted", bubbleColor);
        } else if (unInitiatedButKnownSize || message.isDeleted() || (transferable != null && transferable.getStatus() != Transferable.STATUS_UPLOADING)) {
            if (unInitiatedButKnownSize || (message.isDeleted() && message.getModerated() == null) || transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.download_x_file, UIHelper.getFileDescriptionString(activity, message)), bubbleColor);
            } else if (transferable != null && transferable.getStatus() == Transferable.STATUS_OFFER_CHECK_FILESIZE) {
                displayDownloadableMessage(viewHolder, message, activity.getString(R.string.check_x_filesize, UIHelper.getFileDescriptionString(activity, message)), bubbleColor);
            } else {
                displayInfoMessage(viewHolder, UIHelper.getMessagePreview(activity.xmppConnectionService, message).first, bubbleColor);
            }
        } else if (message.isFileOrImage()
                && message.getEncryption() != Message.ENCRYPTION_PGP
                && message.getEncryption() != Message.ENCRYPTION_DECRYPTION_FAILED) {
            if (message.getFileParams().width > 0 && message.getFileParams().height > 0) {
                displayMediaPreviewMessage(viewHolder, message, bubbleColor);
            } else if (message.getFileParams().runtime > 0) {
                displayAudioMessage(viewHolder, message, bubbleColor);
            } else if ("application/webxdc+zip".equals(message.getFileParams().getMediaType()) && message.getConversation() instanceof Conversation && !message.getFileParams().getCids().isEmpty()) {
                if (message.getThread() != null) {
                    displayWebxdcMessage(viewHolder, message, bubbleColor);
                } else {
                    Element thread = new Element("thread", "jabber:client");
                    thread.setContent(UUID.randomUUID().toString());
                    message.setThread(thread);
                    displayWebxdcMessage(viewHolder, message, bubbleColor);
                }
            } else {
                displayOpenableMessage(viewHolder, message, bubbleColor);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_PGP) {
            if (account.isPgpDecryptionServiceConnected()) {
                if (conversation instanceof Conversation
                        && !account.hasPendingPgpIntent((Conversation) conversation)) {
                    displayInfoMessage(
                            viewHolder,
                            activity.getString(R.string.message_decrypting),
                            bubbleColor);
                } else {
                    displayInfoMessage(
                            viewHolder, activity.getString(R.string.pgp_message), bubbleColor);
                }
            } else {
                displayInfoMessage(
                        viewHolder, activity.getString(R.string.install_openkeychain), bubbleColor);
                viewHolder.messageBox().setOnClickListener(this::promptOpenKeychainInstall);
                viewHolder.messageBody().setOnClickListener(this::promptOpenKeychainInstall);
            }
        } else if (message.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
            displayInfoMessage(
                    viewHolder, activity.getString(R.string.decryption_failed), bubbleColor);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_NOT_FOR_THIS_DEVICE) {
            displayInfoMessage(
                    viewHolder,
                    activity.getString(R.string.not_encrypted_for_this_device),
                    bubbleColor);
        } else if (message.getEncryption() == Message.ENCRYPTION_AXOLOTL_FAILED) {
            displayInfoMessage(
                    viewHolder, activity.getString(R.string.omemo_decryption_failed), bubbleColor);
        } else {
            if (message.wholeIsKnownURI() != null) {
                displayURIMessage(viewHolder, message, bubbleColor);
            } else if (message.isGeoUri()) {
                displayLocationMessage(viewHolder, message, bubbleColor);
            } else if (message.treatAsDownloadable()) {
                try {
                    final URI uri = message.getOob();
                    displayDownloadableMessage(viewHolder,
                            message,
                            activity.getString(
                                    R.string.check_x_filesize_on_host,
                                    UIHelper.getFileDescriptionString(activity, message),
                                    uri.getHost()),
                            bubbleColor);
                } catch (Exception e) {
                    displayDownloadableMessage(
                            viewHolder,
                            message,
                            activity.getString(
                                    R.string.check_x_filesize,
                                    UIHelper.getFileDescriptionString(activity, message)),
                            bubbleColor);
                }
            } else if (message.bodyIsOnlyEmojis() && message.getType() != Message.TYPE_PRIVATE) {
                displayEmojiMessage(viewHolder, message, bubbleColor);
            } else {
                displayTextMessage(viewHolder, message, bubbleColor);
            }
        }
        /*
        if (!black && viewHolder.image().getLayoutParams().width > metrics.density * 110) {
            footerWrap = true;
        }

        viewHolder.messageBoxInner().setMinimumWidth(footerWrap ? (int) (110 * metrics.density) : 0);
        LinearLayout.LayoutParams statusParams = (LinearLayout.LayoutParams) viewHolder.statusLine().getLayoutParams();
        statusParams.width = footerWrap ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        viewHolder.statusLine().setLayoutParams(statusParams);
        */

        final Function<Reaction, GetThumbnailForCid> reactionThumbnailer = (r) -> new Thumbnailer(conversation.getAccount(), r, conversation.canInferPresence());
        if (received) {
            if (!muted && commands != null && conversation instanceof Conversation) {
                CommandButtonAdapter adapter = new CommandButtonAdapter(activity);
                adapter.addAll(commands);
                viewHolder.commandsList().setAdapter(adapter);
                viewHolder.commandsList().setVisibility(View.VISIBLE);
                viewHolder.commandsList().setOnItemClickListener((p, v, pos, id) -> {
                    final Element command = adapter.getItem(pos);
                    if (command != null) {
                        activity.startCommand(conversation.getAccount(), command.getAttributeAsJid("jid"), command.getAttribute("node"));
                    }
                });
            } else {
                // It's unclear if we can set this to null...
                ListAdapter adapter = viewHolder.commandsList().getAdapter();
                if (adapter instanceof ArrayAdapter) {
                    ((ArrayAdapter<?>) adapter).clear();
                }
                viewHolder.commandsList().setVisibility(GONE);
                viewHolder.commandsList().setOnItemClickListener(null);
            }
        }

        setBackgroundTint(viewHolder.messageBox(), bubbleColor);
        setTextColor(viewHolder.messageBody(), bubbleColor);
        viewHolder.messageBody().setLinkTextColor(bubbleToOnSurfaceColor(viewHolder.messageBody(), bubbleColor));

        if (received && viewHolder instanceof StartBubbleMessageItemViewHolder startViewHolder) {
            setTextColor(startViewHolder.encryption(), bubbleColor);
            if (isInValidSession) {
                startViewHolder.encryption().setVisibility(GONE);
            } else {
                startViewHolder.encryption().setVisibility(View.VISIBLE);
                if (omemoEncryption && !message.isTrusted()) {
                    startViewHolder.encryption().setText(R.string.not_trusted);
                } else {
                    startViewHolder
                            .encryption()
                            .setText(CryptoHelper.encryptionTypeToText(message.getEncryption()));
                }
            }
            final var aggregatedReactions = conversation instanceof Conversation ? ((Conversation) conversation).aggregatedReactionsFor(message, reactionThumbnailer) : message.getAggregatedReactions();
            BindingAdapters.setReactionsOnReceived(
                    viewHolder.reactions(),
                    aggregatedReactions,
                    reactions -> sendReactions(message, reactions),
                    emoji -> showDetailedReaction(message, emoji),
                    emoji -> sendCustomReaction(message, emoji),
                    reaction -> removeCustomReaction(conversation, reaction),
                    () -> {
                        if (mConversationFragment.requireTrustKeys()) {
                            return;
                        }

                        final var intent = new Intent(activity, AddReactionActivity.class);
                        intent.putExtra("conversation", message.getConversation().getUuid());
                        intent.putExtra("message", message.getUuid());
                        activity.startActivity(intent);
                    });
        } else {
            if (viewHolder instanceof StartBubbleMessageItemViewHolder startViewHolder) {
                startViewHolder.encryption().setVisibility(View.GONE);
            }
            BindingAdapters.setReactionsOnSent(
                    viewHolder.reactions(),
                    message.getAggregatedReactions(),
                    reactions -> sendReactions(message, reactions),
                    emoji -> showDetailedReaction(message, emoji));
        }

        var subject = message.getSubject();
        if (subject == null && message.getThread() != null) {
            final var thread = ((Conversation) message.getConversation()).getThread(message.getThread().getContent());
            if (thread != null) subject = thread.getSubject();
        }
        if (muted || subject == null) {
            viewHolder.subject().setVisibility(GONE);
        } else {
            viewHolder.subject().setVisibility(View.VISIBLE);
            viewHolder.subject().setText(subject);
        }


        WeakReference<ReplyClickListener> listener = new WeakReference<>(replyClickListener);
        if (message.getInReplyTo() == null) {
            viewHolder.inReplyToBox().setVisibility(GONE);
        } else {
            viewHolder.inReplyToBox().setVisibility(View.VISIBLE);
            viewHolder.inReplyTo().setText(UIHelper.getMessageDisplayName(message.getInReplyTo()));
            viewHolder.inReplyTo().setOnClickListener(v -> {
                ReplyClickListener l = listener.get();
                if (l != null) {
                    l.onReplyClick(message);
                }
            });
            viewHolder.inReplyToQuote().setOnClickListener(v -> {
                ReplyClickListener l = listener.get();
                if (l != null) {
                    l.onReplyClick(message);
                }
            });
            setTextColor(viewHolder.inReplyTo(), bubbleColor);
        }

        if (appSettings.showLinkPreviews()) {
            final var descriptions = message.getLinkDescriptions();
            viewHolder.linkDescriptions().setAdapter(new ArrayAdapter<>(activity, 0, descriptions) {
                @NonNull
                @Override
                public View getView(int position, View view, @NonNull ViewGroup parent) {
                    final LinkDescriptionBinding binding = DataBindingUtil.inflate(LayoutInflater.from(parent.getContext()), R.layout.link_description, parent, false);
                    binding.title.setText(Objects.requireNonNull(getItem(position)).findChildContent("title", "https://ogp.me/ns#"));
                    binding.description.setText(Objects.requireNonNull(getItem(position)).findChildContent("description", "https://ogp.me/ns#"));
                    binding.url.setText(Objects.requireNonNull(getItem(position)).findChildContent("url", "https://ogp.me/ns#"));
                    final var video = Objects.requireNonNull(getItem(position)).findChildContent("video", "https://ogp.me/ns#");
                    if (video != null && !video.isEmpty()) {
                        binding.playButton.setVisibility(View.VISIBLE);
                        binding.playButton.setOnClickListener((v) -> {
                            new FixedURLSpan(video).onClick(v);
                        });
                    }
                    return binding.getRoot();
                }
            });
            Util.justifyListViewHeightBasedOnChildren(viewHolder.linkDescriptions(), (int) (metrics.density * 100), true);
        }

        displayStatus(viewHolder, message, bubbleColor);

        if (message.getConversation().getMode() == Conversation.MODE_SINGLE && viewHolder.username() != null) {
            viewHolder.username().setText(null);
            viewHolder.username().setVisibility(GONE);
        }

       viewHolder.messageBody().setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void sendAccessibilityEvent(@NonNull View host, int eventType) {
                super.sendAccessibilityEvent(host, eventType);
                if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
                    if (viewHolder.messageBody().hasSelection()) {
                        selectionUuid = message.getUuid();
                    } else if (message.getUuid() != null && message.getUuid().equals(selectionUuid)) {
                        selectionUuid = null;
                    }
                }
            }
        });
        return viewHolder.root();
    }

    private void reactionsPopup(
            final Message message,
            final BubbleMessageItemViewHolder viewHolder) {

        // new reactions popup
        Consumer<Collection<String>> callback = reactions -> {
            if (mConversationFragment.requireTrustKeys()) {
                return;
            }
            activity.xmppConnectionService.sendReactions(message, reactions);
        };
        ReactionsConfig config = new ReactionsConfigBuilder(activity)
                .withReactions(new int[]{
                        R.drawable.heart,
                        R.drawable.thumbs_up,
                        R.drawable.thumbs_down,
                        R.drawable.tears_of_joy,
                        R.drawable.astonished,
                        R.drawable.crying,
                        R.drawable.ic_more_horiz_24dp
                })
                .withPopupAlpha(255)
                .withPopupColor(MaterialColors.getColor(viewHolder.messageBox(), com.google.android.material.R.attr.colorSurface))
                .build();
        ReactionPopup popup = new ReactionPopup(activity, config, (positionPopup) -> {
            if (positionPopup.equals(0)) {
                final var aggregated = message.getAggregatedReactions();
                if (aggregated.ourReactions.contains("\u2764\uFE0F")) {
                    callback.accept(aggregated.ourReactions);
                } else {
                    final ImmutableSet.Builder<String> reactionBuilder =
                            new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add("\u2764\uFE0F");
                    callback.accept(reactionBuilder.build());
                }
            } else if (positionPopup.equals(1)) {
                final var aggregated = message.getAggregatedReactions();
                if (aggregated.ourReactions.contains("\uD83D\uDC4D")) {
                    callback.accept(aggregated.ourReactions);
                } else {
                    final ImmutableSet.Builder<String> reactionBuilder =
                            new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add("\uD83D\uDC4D");
                    callback.accept(reactionBuilder.build());
                }
            } else if (positionPopup.equals(2)) {
                final var aggregated = message.getAggregatedReactions();
                if (aggregated.ourReactions.contains("\uD83D\uDC4E")) {
                    callback.accept(aggregated.ourReactions);
                } else {
                    final ImmutableSet.Builder<String> reactionBuilder =
                            new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add("\uD83D\uDC4E");
                    callback.accept(reactionBuilder.build());
                }
            } else if (positionPopup.equals(3)) {
                final var aggregated = message.getAggregatedReactions();
                if (aggregated.ourReactions.contains("\uD83D\uDE02")) {
                    callback.accept(aggregated.ourReactions);
                } else {
                    final ImmutableSet.Builder<String> reactionBuilder =
                            new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add("\uD83D\uDE02");
                    callback.accept(reactionBuilder.build());
                }
            } else if (positionPopup.equals(4)) {
                final var aggregated = message.getAggregatedReactions();
                if (aggregated.ourReactions.contains("\uD83D\uDE32")) {
                    callback.accept(aggregated.ourReactions);
                } else {
                    final ImmutableSet.Builder<String> reactionBuilder =
                            new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add("\uD83D\uDE32");
                    callback.accept(reactionBuilder.build());
                }
            } else if (positionPopup.equals(5)) {
                final var aggregated = message.getAggregatedReactions();
                if (aggregated.ourReactions.contains("\uD83D\uDE22")) {
                    callback.accept(aggregated.ourReactions);
                } else {
                    final ImmutableSet.Builder<String> reactionBuilder =
                            new ImmutableSet.Builder<>();
                    reactionBuilder.addAll(aggregated.ourReactions);
                    reactionBuilder.add("\uD83D\uDE22");
                    callback.accept(reactionBuilder.build());
                }
            } else if (positionPopup.equals(6)) {
                if (mConversationFragment.requireTrustKeys()) {
                    return true;
                }

                final var intent = new Intent(activity, AddReactionActivity.class);
                intent.putExtra("conversation", message.getConversation().getUuid());
                intent.putExtra("message", message.getUuid());
                activity.startActivity(intent);
            }
            return true; // true is closing popup, false is requesting a new selection
        });

        // Single click to show reaction. Don't show reactions popup When it is a link
        LinkClickDetector.setupLinkClickDetector(viewHolder.messageBody());
        final boolean showError =
                message.getStatus() == Message.STATUS_SEND_FAILED
                        && message.getErrorMessage() != null
                        && !Message.ERROR_MESSAGE_CANCELLED.equals(message.getErrorMessage());
        final Conversational conversational = message.getConversation();
        if (conversational instanceof Conversation c) {
            if (!showError
                    && !message.isPrivateMessage()
                    && (message.getEncryption() == Message.ENCRYPTION_NONE
                    || activity.getBooleanPreference("allow_unencrypted_reactions", R.bool.allow_unencrypted_reactions))
                    && !message.isDeleted()
                    && (c.getMode() == Conversational.MODE_SINGLE
                    || (c.getMucOptions().occupantId()
                    && c.getMucOptions().participating()))) {
                viewHolder.messageBox().setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                            popup.setFocusable(false);
                            popup.onTouch(v, event);
                        }
                    }
                    return false;
                });

                viewHolder.messageBody().setOnTouchListener((v, event) -> {
                    boolean isLink = LinkClickDetector.isLinkClicked(viewHolder.messageBody(), event);
                    if (event.getAction() == MotionEvent.ACTION_UP && !isLink) {
                        if (MessageAdapter.this.mOnMessageBoxClickedListener != null) {
                            popup.setFocusable(false);
                            popup.onTouch(v, event);
                        }
                    }
                    return false;
                });
            }
        }
    }


    private View render(
            final Message message, final DateSeperatorMessageItemViewHolder viewHolder) {
        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        if (UIHelper.today(message.getTimeSent())) {
            viewHolder.binding.messageBody.setText(R.string.today);
        } else if (UIHelper.yesterday(message.getTimeSent())) {
            viewHolder.binding.messageBody.setText(R.string.yesterday);
        } else {
            viewHolder.binding.messageBody.setText(
                    DateUtils.formatDateTime(
                            activity,
                            message.getTimeSent(),
                            DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR));
        }
        if (colorfulBackground) {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.PRIMARY);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.PRIMARY);
        } else {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.SURFACE_HIGH);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.SURFACE_HIGH);
        }
        return viewHolder.binding.getRoot();
    }

    private View render(final Message message, final RtpSessionMessageItemViewHolder viewHolder) {
        final boolean colorfulBackground = this.bubbleDesign.colorfulChatBubbles;
        final boolean received = message.getStatus() <= Message.STATUS_RECEIVED;
        final RtpSessionStatus rtpSessionStatus = RtpSessionStatus.of(message.getBody());
        final long duration = rtpSessionStatus.duration;
        if (received) {
            if (duration > 0) {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.incoming_call_duration_timestamp,
                                TimeFrameUtils.resolve(activity, duration),
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            } else if (rtpSessionStatus.successful) {
                viewHolder.binding.messageBody.setText(R.string.incoming_call);
            } else {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.missed_call_timestamp,
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            }
        } else {
            if (duration > 0) {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.outgoing_call_duration_timestamp,
                                TimeFrameUtils.resolve(activity, duration),
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            } else {
                viewHolder.binding.messageBody.setText(
                        activity.getString(
                                R.string.outgoing_call_timestamp,
                                UIHelper.readableTimeDifferenceFull(
                                        activity, message.getTimeSent())));
            }
        }
        if (colorfulBackground) {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.SECONDARY);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.SECONDARY);
            setImageTint(viewHolder.binding.indicatorReceived, BubbleColor.SECONDARY);
        } else {
            setBackgroundTint(viewHolder.binding.messageBox, BubbleColor.SURFACE_HIGH);
            setTextColor(viewHolder.binding.messageBody, BubbleColor.SURFACE_HIGH);
            setImageTint(viewHolder.binding.indicatorReceived, BubbleColor.SURFACE_HIGH);
        }
        viewHolder.binding.indicatorReceived.setImageResource(
                RtpSessionStatus.getDrawable(received, rtpSessionStatus.successful));
        return viewHolder.binding.getRoot();
    }

    private View render(final Message message, final StatusMessageItemViewHolder viewHolder) {
        final var conversation = message.getConversation();
        if ("LOAD_MORE".equals(message.getBody())) {
            viewHolder.binding.statusMessage.setVisibility(View.GONE);
            viewHolder.binding.messagePhoto.setVisibility(View.GONE);
            viewHolder.binding.loadMoreMessages.setVisibility(View.VISIBLE);
            viewHolder.binding.loadMoreMessages.setOnClickListener(
                    v -> loadMoreMessages((Conversation) message.getConversation()));
        } else {
            viewHolder.binding.statusMessage.setVisibility(View.VISIBLE);
            viewHolder.binding.loadMoreMessages.setVisibility(View.GONE);
            viewHolder.binding.statusMessage.setText(message.getBody());
            boolean showAvatar;
            if (conversation.getMode() == Conversation.MODE_SINGLE) {
                showAvatar = true;
                AvatarWorkerTask.loadAvatar(
                        message, viewHolder.binding.messagePhoto, R.dimen.avatar_on_status_message);
            } else if (message.getCounterpart() != null
                    || message.getTrueCounterpart() != null
                    || (message.getCounterparts() != null
                    && !message.getCounterparts().isEmpty())) {
                showAvatar = true;
                AvatarWorkerTask.loadAvatar(
                        message, viewHolder.binding.messagePhoto, R.dimen.avatar_on_status_message);
            } else {
                showAvatar = false;
            }
            if (showAvatar) {
                viewHolder.binding.messagePhoto.setAlpha(0.5f);
                viewHolder.binding.messagePhoto.setVisibility(View.VISIBLE);
            } else {
                viewHolder.binding.messagePhoto.setVisibility(View.GONE);
            }
        }
        return viewHolder.binding.getRoot();
}

    private void setAvatarDistance(
            final LinearLayout messageBox,
            final Class<? extends BubbleMessageItemViewHolder> clazz,
            final boolean showAvatar) {
        final ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) messageBox.getLayoutParams();
        if (showAvatar) {
            final var resources = messageBox.getResources();
            if (clazz == StartBubbleMessageItemViewHolder.class) {
                layoutParams.setMarginStart(
                        resources.getDimensionPixelSize(R.dimen.bubble_avatar_distance));
                layoutParams.setMarginEnd(0);
            } else if (clazz == EndBubbleMessageItemViewHolder.class) {
                layoutParams.setMarginStart(0);
                layoutParams.setMarginEnd(
                        resources.getDimensionPixelSize(R.dimen.bubble_avatar_distance));
            } else {
                throw new AssertionError("Avatar distances are not available on this view type");
            }
        } else {
            layoutParams.setMarginStart(0);
            layoutParams.setMarginEnd(0);
        }
        messageBox.setLayoutParams(layoutParams);
    }

    private void setBubblePadding(
            final SwipeLayout root,
            final boolean mergeIntoTop,
            final boolean mergeIntoBottom) {
        final var resources = root.getResources();
        final var horizontal = resources.getDimensionPixelSize(R.dimen.bubble_horizontal_padding);
        final int top =
                resources.getDimensionPixelSize(
                        mergeIntoTop
                                ? R.dimen.bubble_vertical_padding_minimum
                                : R.dimen.bubble_vertical_padding);
        final int bottom =
                resources.getDimensionPixelSize(
                        mergeIntoBottom
                                ? R.dimen.bubble_vertical_padding_minimum
                                : R.dimen.bubble_vertical_padding);
        root.setPadding(horizontal, top, horizontal, bottom);
    }

    private void setRequiresAvatar(
            final BubbleMessageItemViewHolder viewHolder, final boolean requiresAvatar, Message message) {
        final var layoutParams = viewHolder.contactPicture().getLayoutParams();
        final boolean multiReceived =
                message.getConversation().getMode() == Conversation.MODE_MULTI
                        && message.getStatus() <= Message.STATUS_RECEIVED;
        if (requiresAvatar) {
            final var resources = viewHolder.contactPicture().getResources();
            final var avatarSize = resources.getDimensionPixelSize(R.dimen.bubble_avatar_size);
            layoutParams.height = avatarSize;
            viewHolder.contactPicture().setVisibility(View.VISIBLE);
            viewHolder.messageBox().setMinimumHeight(avatarSize);
            if (mForceNames || multiReceived || (message.getTrueCounterpart() != null && message.getContact() != null)) {
                final String displayName = UIHelper.getMessageDisplayName(message);
                if (viewHolder.username() != null && displayName != null) {
                    viewHolder.username().setVisibility(View.VISIBLE);
                    viewHolder.username().setText(UIHelper.getColoredUsername(activity.xmppConnectionService, message));
                }
            } else if (viewHolder.username() != null) {
                viewHolder.username().setText(null);
                viewHolder.username().setVisibility(GONE);
            }
        } else {
            layoutParams.height = 0;
            viewHolder.contactPicture().setVisibility(View.INVISIBLE);
            if (viewHolder.username() != null) {
                viewHolder.username().setText(null);
                viewHolder.username().setVisibility(GONE);
            }
            viewHolder.messageBox().setMinimumHeight(0);
        }
        viewHolder.contactPicture().setLayoutParams(layoutParams);
    }

    private boolean mergeIntoTop(final int position, final Message message) {
        if (position < 0) {
            return false;
        }
        final var top = getItem(position - 1);
        return merge(top, message);
    }

    private boolean mergeIntoBottom(final int position, final Message message) {
        final Message bottom;
        try {
            bottom = getItem(position + 1);
        } catch (final IndexOutOfBoundsException e) {
            return false;
        }
        return merge(message, bottom);
    }

    private static boolean merge(final Message a, final Message b) {
        if (getItemViewType(a, false) != getItemViewType(b, false)) {
            return false;
        }
        final var receivedA = a.getStatus() == Message.STATUS_RECEIVED;
        final var receivedB = b.getStatus() == Message.STATUS_RECEIVED;
        if (receivedA != receivedB) {
            return false;
        }
        if (a.getConversation().getMode() == Conversation.MODE_MULTI
                && a.getStatus() == Message.STATUS_RECEIVED) {
            final var occupantIdA = a.getOccupantId();
            final var occupantIdB = b.getOccupantId();
            if (occupantIdA != null && occupantIdB != null) {
                if (!occupantIdA.equals(occupantIdB)) {
                    return false;
                }
            }
            final var counterPartA = a.getCounterpart();
            final var counterPartB = b.getCounterpart();
            if (counterPartA == null || !counterPartA.equals(counterPartB)) {
                return false;
            }
        }
        return b.getTimeSent() - a.getTimeSent() <= Config.MESSAGE_MERGE_WINDOW;
    }

    private boolean showDetailedReaction(final Message message, Map.Entry<EmojiSearch.Emoji, Collection<Reaction>> reaction) {
        final var c = message.getConversation();
        if (c instanceof Conversation conversation && c.getMode() == Conversational.MODE_MULTI) {
            final var reactions = reaction.getValue();
            final var mucOptions = conversation.getMucOptions();
            final var users = mucOptions.findUsers(reactions);
            if (users.isEmpty()) {
                return true;
            }
            final MaterialAlertDialogBuilder dialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            dialogBuilder.setTitle(reaction.getKey().toString());
            dialogBuilder.setMessage(UIHelper.concatNames(users));
            dialogBuilder.create().show();
            return true;
        } else {
            return false;
        }
    }

    private void sendReactions(final Message message, final Collection<String> reactions) {
        if (mConversationFragment.requireTrustKeys()) {
            return;
        }
        if (!message.isPrivateMessage() && activity.xmppConnectionService.sendReactions(message, reactions)) {
            return;
        }
        Toast.makeText(activity, R.string.could_not_add_reaction, Toast.LENGTH_LONG).show();
    }

    private void sendCustomReaction(final Message inReplyTo, final EmojiSearch.CustomEmoji emoji) {
        if (mConversationFragment.requireTrustKeys()) {
            return;
        }
        final var message = inReplyTo.reply();
        message.appendBody(emoji.toInsert());
        Message.configurePrivateMessage(message);
        new Thread(() -> activity.xmppConnectionService.sendMessage(message)).start();
    }

    private void removeCustomReaction(final Conversational conversation, final Reaction reaction) {
        if (mConversationFragment.requireTrustKeys()) {
            return;
        }
        if (!(conversation instanceof Conversation)) {
            Toast.makeText(activity, R.string.could_not_add_reaction, Toast.LENGTH_LONG).show();
            return;
        }

        final var message = new Message(conversation, " ", ((Conversation) conversation).getNextEncryption());
        final var envelope = ((Conversation) conversation).findMessageWithUuidOrRemoteId(reaction.envelopeId);
        if (envelope != null) {
            ((Conversation) conversation).remove(envelope);
            message.addPayload(envelope.getReply());
            message.getOrMakeHtml();
            message.putEdited(reaction.envelopeId, envelope.getServerMsgId());
        } else {
            message.putEdited(reaction.envelopeId, null);
        }

        new Thread(() -> activity.xmppConnectionService.sendMessage(message)).start();
    }

    private void addReaction(final Message message) {
        if (mConversationFragment.requireTrustKeys()) {
            return;
        }
        activity.addReaction(
                message,
                reactions -> {
                    if (activity.xmppConnectionService.sendReactions(message, reactions)) {
                        return;
                    }
                    Toast.makeText(activity, R.string.could_not_add_reaction, Toast.LENGTH_LONG)
                            .show();
                });
    }

    private void promptOpenKeychainInstall(View view) {
        activity.showInstallPgpDialog();
    }

    public FileBackend getFileBackend() {
        return activity.xmppConnectionService.getFileBackend();
    }

    public void stopAudioPlayer() {
        audioPlayer.stop();
    }

    public void unregisterListenerInAudioPlayer() {
        audioPlayer.unregisterListener();
    }

    public void startStopPending() {
        audioPlayer.startStopPending();
    }

    public void openDownloadable(Message message) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(
                                activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            ConversationFragment.registerPendingMessage(activity, message);
            ActivityCompat.requestPermissions(
                    activity,
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    ConversationsActivity.REQUEST_OPEN_MESSAGE);
            return;
        }
        final DownloadableFile file =
                activity.xmppConnectionService.getFileBackend().getFile(message);
        final var fp = message.getFileParams();
        final var name = fp == null ? null : fp.getName();
        final var displayName = name == null ? file.getName() : name;
        ViewUtil.view(activity, file, displayName);
    }

    private void showLocation(Message message) {
        for (Intent intent : GeoHelper.createGeoIntentsFromMessage(activity, message)) {
            if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                getContext().startActivity(intent);
                return;
            }
        }
        Toast.makeText(
                        activity,
                        R.string.no_application_found_to_display_location,
                        Toast.LENGTH_SHORT)
                .show();
    }

    public void updatePreferences() {
        this.bubbleDesign =
                new BubbleDesign(
                        appSettings.isColorfulChatBubbles(),
                       appSettings.isAlignStart(),
                        appSettings.isLargeFont(),
                        appSettings.isShowAvatars());
    }

    public void setHighlightedTerm(List<String> terms) {
        this.highlightedTerm = terms == null ? null : StylingHelper.filterHighlightedWords(terms);
    }

    public interface OnContactPictureClicked {
        void onContactPictureClicked(Message message);
    }

    public interface OnContactPictureLongClicked {
        void onContactPictureLongClicked(View v, Message message);
    }

    public interface OnInlineImageLongClicked {
        boolean onInlineImageLongClicked(Cid cid);
    }

    private static void setBackgroundTint(final LinearLayout view, final BubbleColor bubbleColor) {
        view.setBackgroundTintList(bubbleToColorStateList(view, bubbleColor));
    }

    private static ColorStateList bubbleToColorStateList(
            final View view, final BubbleColor bubbleColor) {
        final @AttrRes int colorAttributeResId =
                switch (bubbleColor) {
                    case SURFACE ->
                            Activities.isNightMode(view.getContext())
                                    ? com.google.android.material.R.attr.colorSurfaceBright
                                    : com.google.android.material.R.attr.colorOnSurfaceInverse;
                    case SURFACE_HIGH -> com.google.android.material.R.attr
                            .colorSurfaceContainerHigh;
                    case PRIMARY -> com.google.android.material.R.attr.colorPrimaryContainer;
                    case SECONDARY -> com.google.android.material.R.attr.colorSecondaryContainer;
                    case TERTIARY -> com.google.android.material.R.attr.colorTertiaryContainer;
                    case WARNING -> com.google.android.material.R.attr.colorErrorContainer;
                };
        return ColorStateList.valueOf(MaterialColors.getColor(view, colorAttributeResId));
    }

    public static void setImageTint(final ImageView imageView, final BubbleColor bubbleColor) {
        ImageViewCompat.setImageTintList(
                imageView, bubbleToOnSurfaceColorStateList(imageView, bubbleColor));
    }

    public static void setImageTintError(final ImageView imageView) {
        ImageViewCompat.setImageTintList(
                imageView,
                ColorStateList.valueOf(
                        MaterialColors.getColor(imageView, androidx.appcompat.R.attr.colorError)));
    }

    public static void setTextColor(final TextView textView, final BubbleColor bubbleColor) {
        final var color = bubbleToOnSurfaceColor(textView, bubbleColor);
        textView.setTextColor(color);
        if (BubbleColor.SURFACES.contains(bubbleColor)) {
            textView.setLinkTextColor(
                    MaterialColors.getColor(textView, androidx.appcompat.R.attr.colorPrimary));
        } else {
            textView.setLinkTextColor(color);
        }
    }

    private static void setTextSize(final TextView textView, final boolean largeFont) {
        if (largeFont) {
            textView.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        } else {
            textView.setTextAppearance(
                    com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        }
    }

    private static @ColorInt int bubbleToOnSurfaceVariant(
            final View view, final BubbleColor bubbleColor) {
        final @AttrRes int colorAttributeResId;
        if (BubbleColor.SURFACES.contains(bubbleColor)) {
            colorAttributeResId = com.google.android.material.R.attr.colorOnSurfaceVariant;
        } else {
            colorAttributeResId = bubbleToOnSurface(bubbleColor);
        }
        return MaterialColors.getColor(view, colorAttributeResId);
    }

    private static @ColorInt int bubbleToOnSurfaceColor(
            final View view, final BubbleColor bubbleColor) {
        return MaterialColors.getColor(view, bubbleToOnSurface(bubbleColor));
    }

    public static ColorStateList bubbleToOnSurfaceColorStateList(
            final View view, final BubbleColor bubbleColor) {
        return ColorStateList.valueOf(bubbleToOnSurfaceColor(view, bubbleColor));
    }

    private static @AttrRes int bubbleToOnSurface(final BubbleColor bubbleColor) {
        return switch (bubbleColor) {
            case SURFACE, SURFACE_HIGH -> com.google.android.material.R.attr.colorOnSurface;
            case PRIMARY -> com.google.android.material.R.attr.colorOnPrimaryContainer;
            case SECONDARY -> com.google.android.material.R.attr.colorOnSecondaryContainer;
            case TERTIARY -> com.google.android.material.R.attr.colorOnTertiaryContainer;
            case WARNING -> com.google.android.material.R.attr.colorOnErrorContainer;
        };
    }

    public enum BubbleColor {
        SURFACE,
        SURFACE_HIGH,
        PRIMARY,
        SECONDARY,
        TERTIARY,
        WARNING;

        private static final Collection<BubbleColor> SURFACES =
                Arrays.asList(BubbleColor.SURFACE, BubbleColor.SURFACE_HIGH);
    }

    private static class BubbleDesign {
        public final boolean colorfulChatBubbles;
        public final boolean alignStart;
        public final boolean largeFont;
        public final boolean showAvatars;

        private BubbleDesign(
                final boolean colorfulChatBubbles,
               final boolean alignStart,
                final boolean largeFont,
                final boolean showAvatars) {
            this.colorfulChatBubbles = colorfulChatBubbles;
            this.alignStart = alignStart;
            this.largeFont = largeFont;
            this.showAvatars = showAvatars;
        }
    }

    private abstract static class MessageItemViewHolder extends RecyclerView.ViewHolder {

        private final View itemView;

        private MessageItemViewHolder(@NonNull View itemView) {
            super(itemView);
            this.itemView = itemView;
        }
    }

    private abstract static class BubbleMessageItemViewHolder extends MessageItemViewHolder {

        private BubbleMessageItemViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        public abstract SwipeLayout root();

        protected abstract ImageView indicatorEdit();

        protected abstract RelativeLayout audioPlayer();

        protected abstract LinearLayout messageBox();

        protected abstract MaterialButton downloadButton();

        protected abstract ShapeableImageView image();

        protected abstract ImageView indicatorSecurity();

        protected abstract ImageView indicatorReceived();

        protected abstract TextView time();

        protected abstract TextView messageBody();

        protected abstract ImageView contactPicture();

        protected abstract ChipGroup reactions();

        protected abstract ListView commandsList();

        protected abstract View messageBoxInner();

        protected abstract View statusLine();

        protected abstract GithubIdenticonView threadIdenticon();

        protected abstract ListView linkDescriptions();

        protected abstract LinearLayout inReplyToBox();

        protected abstract TextView inReplyTo();

        protected abstract TextView inReplyToQuote();

        protected abstract TextView subject();

        protected abstract TextView username();

        protected abstract SwipeLayout layoutSwipe();

        protected abstract RelativeLayout bottomWrapper();

        protected abstract TextView showMore();
    }

    private static class StartBubbleMessageItemViewHolder extends BubbleMessageItemViewHolder {

        private final ItemMessageStartBinding binding;

        public StartBubbleMessageItemViewHolder(@NonNull ItemMessageStartBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public SwipeLayout root() {
            return (SwipeLayout) this.binding.getRoot();
        }

        @Override
        protected ImageView indicatorEdit() {
            return this.binding.editIndicator;
        }

        @Override
        protected RelativeLayout audioPlayer() {
            return this.binding.messageContent.audioPlayer;
        }

        @Override
        protected LinearLayout messageBox() {
            return this.binding.messageBox;
        }

        @Override
        protected MaterialButton downloadButton() {
            return this.binding.messageContent.downloadButton;
        }

        @Override
        protected ShapeableImageView image() {
            return this.binding.messageContent.messageImage;
        }

        protected ImageView indicatorSecurity() {
            return this.binding.securityIndicator;
        }

        @Override
        protected ImageView indicatorReceived() {
            return this.binding.indicatorReceived;
        }

        @Override
        protected TextView time() {
            return this.binding.messageTime;
        }

        @Override
        protected TextView messageBody() {
            return this.binding.messageContent.messageBody;
        }

        protected TextView encryption() {
            return this.binding.messageEncryption;
        }

        @Override
        protected ImageView contactPicture() {
            return this.binding.messagePhoto;
        }

        @Override
        protected ChipGroup reactions() {
            return this.binding.reactions;
        }

        @Override
        protected ListView commandsList() {
            return this.binding.messageContent.commandsList;
        }

        @Override
        protected View messageBoxInner() {
            return this.binding.messageBoxInner;
        }

        @Override
        protected View statusLine() {
            return this.binding.statusLine;
        }

        @Override
        protected TextView username() {
            return this.binding.messageUsername;
        }

        @Override
        protected GithubIdenticonView threadIdenticon() {
            return this.binding.threadIdenticon;
        }

        @Override
        protected ListView linkDescriptions() {
            return this.binding.messageContent.linkDescriptions;
        }

        @Override
        protected LinearLayout inReplyToBox() {
            return this.binding.messageContent.inReplyToBox;
        }

        @Override
        protected TextView inReplyTo() {
            return this.binding.messageContent.inReplyTo;
        }

        @Override
        protected TextView inReplyToQuote() {
            return this.binding.messageContent.inReplyToQuote;
        }

        @Override
        protected TextView subject() {
            return this.binding.messageSubject;
        }

        @Override
        protected TextView showMore() {
            return this.binding.messageContent.showMore;
        }

        @Override
        protected SwipeLayout layoutSwipe() {
            return this.binding.layoutSwipe;
        }

        @Override
        protected RelativeLayout bottomWrapper() {
            return this.binding.bottomWrapper;
        }
    }

    private static class EndBubbleMessageItemViewHolder extends BubbleMessageItemViewHolder {

        private final ItemMessageEndBinding binding;

        private EndBubbleMessageItemViewHolder(@NonNull ItemMessageEndBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @Override
        public SwipeLayout root() {
            return (SwipeLayout) this.binding.getRoot();
        }

        @Override
        protected TextView username() {
            return null;
        }

        @Override
        protected SwipeLayout layoutSwipe() {
            return this.binding.layoutSwipe;
        }

        @Override
        protected RelativeLayout bottomWrapper() {
            return this.binding.bottomWrapper;
        }

        @Override
        protected TextView showMore() {
            return this.binding.messageContent.showMore;
        }

        @Override
        protected ImageView indicatorEdit() {
            return this.binding.editIndicator;
        }

        @Override
        protected RelativeLayout audioPlayer() {
            return this.binding.messageContent.audioPlayer;
        }

        @Override
        protected LinearLayout messageBox() {
            return this.binding.messageBox;
        }

        @Override
        protected MaterialButton downloadButton() {
            return this.binding.messageContent.downloadButton;
        }

        @Override
        protected ShapeableImageView image() {
            return this.binding.messageContent.messageImage;
        }

        @Override
        protected ImageView indicatorSecurity() {
            return this.binding.securityIndicator;
        }

        @Override
        protected ImageView indicatorReceived() {
            return this.binding.indicatorReceived;
        }

        @Override
        protected TextView time() {
            return this.binding.messageTime;
        }

        @Override
        protected TextView messageBody() {
            return this.binding.messageContent.messageBody;
        }

        @Override
        protected ImageView contactPicture() {
            return this.binding.messagePhoto;
        }

        @Override
        protected ChipGroup reactions() {
            return this.binding.reactions;
        }

        @Override
        protected ListView commandsList() {
            return this.binding.messageContent.commandsList;
        }

        @Override
        protected View messageBoxInner() {
            return this.binding.messageBoxInner;
        }

        @Override
        protected View statusLine() {
            return this.binding.statusLine;
        }

        @Override
        protected GithubIdenticonView threadIdenticon() {
            return this.binding.threadIdenticon;
        }

        @Override
        protected ListView linkDescriptions() {
            return this.binding.messageContent.linkDescriptions;
        }

        @Override
        protected LinearLayout inReplyToBox() {
            return this.binding.messageContent.inReplyToBox;
        }

        @Override
        protected TextView inReplyTo() {
            return this.binding.messageContent.inReplyTo;
        }

        @Override
        protected TextView inReplyToQuote() {
            return this.binding.messageContent.inReplyToQuote;
        }

        @Override
        protected TextView subject() {
            return this.binding.messageSubject;
        }
    }

    private static class DateSeperatorMessageItemViewHolder extends MessageItemViewHolder {

        private final ItemMessageDateBubbleBinding binding;

        private DateSeperatorMessageItemViewHolder(@NonNull ItemMessageDateBubbleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class RtpSessionMessageItemViewHolder extends MessageItemViewHolder {

        private final ItemMessageRtpSessionBinding binding;

        private RtpSessionMessageItemViewHolder(@NonNull ItemMessageRtpSessionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private static class StatusMessageItemViewHolder extends MessageItemViewHolder {

        private final ItemMessageStatusBinding binding;

        private StatusMessageItemViewHolder(@NonNull ItemMessageStatusBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    class Thumbnailer implements GetThumbnailForCid {
        final Account account;
        final boolean canFetch;
        final Jid counterpart;

        public Thumbnailer(final Message message) {
            account = message.getConversation().getAccount();
            canFetch = message.trusted() || message.getConversation().canInferPresence();
            counterpart = message.getCounterpart();
        }

        public Thumbnailer(final Account account, final Reaction reaction, final boolean allowFetch) {
            canFetch = allowFetch;
            counterpart = reaction.from;
            this.account = account;
        }

        @Override
        public Drawable getThumbnail(Cid cid) {
            try {
                DownloadableFile f = activity.xmppConnectionService.getFileForCid(cid);
                if (f == null || !f.canRead()) {
                    if (!canFetch) return null;

                    try {
                        new BobTransfer(BobTransfer.uri(cid), account, counterpart, activity.xmppConnectionService).start();
                    } catch (final NoSuchAlgorithmException | URISyntaxException ignored) { }
                    return null;
                }

                Drawable d = activity.xmppConnectionService.getFileBackend().getThumbnail(f, activity.getResources(), (int) (metrics.density * 288), true);
                if (d == null) {
                    new ThumbnailTask().execute(f);
                }
                return d;
            } catch (final IOException e) {
                return null;
            }
        }
    }

    class ThumbnailTask extends AsyncTask<DownloadableFile, Void, Drawable[]> {
        @Override
        protected Drawable[] doInBackground(DownloadableFile... params) {
            if (isCancelled()) return null;

            Drawable[] d = new Drawable[params.length];
            for (int i = 0; i < params.length; i++) {
                try {
                    d[i] = activity.xmppConnectionService.getFileBackend().getThumbnail(params[i], activity.getResources(), (int) (metrics.density * 288), false);
                } catch (final IOException e) {
                    d[i] = null;
                }
            }

            return d;
        }

        @Override
        protected void onPostExecute(final Drawable[] d) {
            if (isCancelled()) return;
            activity.xmppConnectionService.updateConversationUi();
        }
    }

    private Conversation wrap(Conversational conversational) {
        if (conversational instanceof Conversation) {
            return (Conversation) conversational;
        } else {
            return activity.xmppConnectionService.findOrCreateConversation(conversational.getAccount(),
                    conversational.getJid(),
                    conversational.getMode() == Conversational.MODE_MULTI,
                    true,
                    true);
        }
    }

    public interface ReplyClickListener {
        void onReplyClick(Message message);
    }
}
