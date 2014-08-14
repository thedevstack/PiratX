package eu.siacs.conversations.ui.adapter;

import java.util.HashMap;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.utils.UIHelper;
import eu.siacs.conversations.xmpp.jingle.JingleConnection;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.Html;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MessageAdapter extends ArrayAdapter<Message> {

	private static final int SENT = 0;
	private static final int RECIEVED = 1;
	private static final int STATUS = 2;

	private ConversationActivity activity;

	private Bitmap accountBitmap;

	private BitmapCache mBitmapCache = new BitmapCache();
	private DisplayMetrics metrics;

	private OnContactPictureClicked mOnContactPictureClickedListener;
	private OnContactPictureLongClicked mOnContactPictureLongClickedListener;

	public MessageAdapter(ConversationActivity activity, List<Message> messages) {
		super(activity, 0, messages);
		this.activity = activity;
		metrics = getContext().getResources().getDisplayMetrics();
	}

	private Bitmap getSelfBitmap() {
		if (this.accountBitmap == null) {

			if (getCount() > 0) {
				this.accountBitmap = getItem(0).getConversation().getAccount()
						.getImage(getContext(), 48);
			}
		}
		return this.accountBitmap;
	}

	public void setOnContactPictureClicked(OnContactPictureClicked listener) {
		this.mOnContactPictureClickedListener = listener;
	}
	
	public void setOnContactPictureLongClicked(OnContactPictureLongClicked listener) {
		this.mOnContactPictureLongClickedListener = listener;
	}

	@Override
	public int getViewTypeCount() {
		return 3;
	}

	@Override
	public int getItemViewType(int position) {
		if (getItem(position).getType() == Message.TYPE_STATUS) {
			return STATUS;
		} else if (getItem(position).getStatus() <= Message.STATUS_RECIEVED) {
			return RECIEVED;
		} else {
			return SENT;
		}
	}

	private void displayStatus(ViewHolder viewHolder, Message message) {
		String filesize = null;
		String info = null;
		boolean error = false;
		boolean multiReceived = message.getConversation().getMode() == Conversation.MODE_MULTI
				&& message.getStatus() <= Message.STATUS_RECIEVED;
		if (message.getType() == Message.TYPE_IMAGE) {
			String[] fileParams = message.getBody().split(",");
			try {
				long size = Long.parseLong(fileParams[0]);
				filesize = size / 1024 + " KB";
			} catch (NumberFormatException e) {
				filesize = "0 KB";
			}
		}
		switch (message.getStatus()) {
		case Message.STATUS_WAITING:
			info = getContext().getString(R.string.waiting);
			break;
		case Message.STATUS_UNSEND:
			info = getContext().getString(R.string.sending);
			break;
		case Message.STATUS_OFFERED:
			info = getContext().getString(R.string.offering);
			break;
		case Message.STATUS_SEND_FAILED:
			info = getContext().getString(R.string.send_failed);
			error = true;
			break;
		case Message.STATUS_SEND_REJECTED:
			info = getContext().getString(R.string.send_rejected);
			error = true;
			break;
		case Message.STATUS_RECEPTION_FAILED:
			info = getContext().getString(R.string.reception_failed);
			error = true;
		default:
			if (multiReceived) {
				Contact contact = message.getContact();
				if (contact != null) {
					info = contact.getDisplayName();
				} else {
					if (message.getPresence() != null) {
						info = message.getPresence();
					} else {
						info = message.getCounterpart();
					}
				}
			}
			break;
		}
		if (error) {
			viewHolder.time.setTextColor(0xFFe92727);
		} else {
			viewHolder.time.setTextColor(activity.getSecondaryTextColor());
		}
		if (message.getEncryption() == Message.ENCRYPTION_NONE) {
			viewHolder.indicator.setVisibility(View.GONE);
		} else {
			viewHolder.indicator.setVisibility(View.VISIBLE);
		}

		String formatedTime = UIHelper.readableTimeDifference(getContext(),
				message.getTimeSent());
		if (message.getStatus() <= Message.STATUS_RECIEVED) {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + info);
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(formatedTime + " \u00B7 " + filesize);
			} else {
				viewHolder.time.setText(formatedTime);
			}
		} else {
			if ((filesize != null) && (info != null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + info);
			} else if ((filesize == null) && (info != null)) {
				if (error) {
					viewHolder.time.setText(info + " \u00B7 " + formatedTime);
				} else {
					viewHolder.time.setText(info);
				}
			} else if ((filesize != null) && (info == null)) {
				viewHolder.time.setText(filesize + " \u00B7 " + formatedTime);
			} else {
				viewHolder.time.setText(formatedTime);
			}
		}
	}

	private void displayInfoMessage(ViewHolder viewHolder, int r) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(getContext().getString(r));
		viewHolder.messageBody.setTextColor(0xff33B5E5);
		viewHolder.messageBody.setTypeface(null, Typeface.ITALIC);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayDecryptionFailed(ViewHolder viewHolder) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		viewHolder.messageBody.setText(getContext().getString(
				R.string.decryption_failed));
		viewHolder.messageBody.setTextColor(0xFFe92727);
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setTextIsSelectable(false);
	}

	private void displayTextMessage(ViewHolder viewHolder, Message message) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.image.setVisibility(View.GONE);
		viewHolder.messageBody.setVisibility(View.VISIBLE);
		if (message.getBody() != null) {
			if (message.getType() != Message.TYPE_PRIVATE) {
				viewHolder.messageBody.setText(message.getBody().trim());
			} else {
				StringBuilder builder = new StringBuilder();
				builder.append(message.getBody().trim());
				builder.append("&nbsp;<b><i>(");
				if (message.getStatus() <= Message.STATUS_RECIEVED) {
					builder.append(activity.getString(R.string.private_message));
				} else {
					String to;
					if (message.getPresence() != null) {
						to = message.getPresence();
					} else {
						to = message.getCounterpart();
					}
					builder.append(activity.getString(R.string.private_message_to, to));
				}
				builder.append(")</i></b>");
				viewHolder.messageBody.setText(Html.fromHtml(builder.toString()));
			}
		} else {
			viewHolder.messageBody.setText("");
		}
		viewHolder.messageBody.setTextColor(activity.getPrimaryTextColor());
		viewHolder.messageBody.setTypeface(null, Typeface.NORMAL);
		viewHolder.messageBody.setTextIsSelectable(true);
	}

	private void displayImageMessage(ViewHolder viewHolder,
			final Message message) {
		if (viewHolder.download_button != null) {
			viewHolder.download_button.setVisibility(View.GONE);
		}
		viewHolder.messageBody.setVisibility(View.GONE);
		viewHolder.image.setVisibility(View.VISIBLE);
		String[] fileParams = message.getBody().split(",");
		if (fileParams.length == 3) {
			double target = metrics.density * 288;
			int w = Integer.parseInt(fileParams[1]);
			int h = Integer.parseInt(fileParams[2]);
			int scalledW;
			int scalledH;
			if (w <= h) {
				scalledW = (int) (w / ((double) h / target));
				scalledH = (int) target;
			} else {
				scalledW = (int) target;
				scalledH = (int) (h / ((double) w / target));
			}
			viewHolder.image.setLayoutParams(new LinearLayout.LayoutParams(
					scalledW, scalledH));
		}
		activity.loadBitmap(message, viewHolder.image);
		viewHolder.image.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(activity.xmppConnectionService
						.getFileBackend().getJingleFileUri(message), "image/*");
				getContext().startActivity(intent);
			}
		});
		viewHolder.image.setOnLongClickListener(new OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				Intent shareIntent = new Intent();
				shareIntent.setAction(Intent.ACTION_SEND);
				shareIntent.putExtra(Intent.EXTRA_STREAM,
						activity.xmppConnectionService.getFileBackend()
								.getJingleFileUri(message));
				shareIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
				shareIntent.setType("image/webp");
				getContext().startActivity(
						Intent.createChooser(shareIntent,
								getContext().getText(R.string.share_with)));
				return true;
			}
		});
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		final Message item = getItem(position);
		int type = getItemViewType(position);
		ViewHolder viewHolder;
		if (view == null) {
			viewHolder = new ViewHolder();
			switch (type) {
			case SENT:
				view = (View) activity.getLayoutInflater().inflate(
						R.layout.message_sent, parent, false);
				viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
				viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
				viewHolder.contact_picture.setImageBitmap(getSelfBitmap());
				viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
				viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
				viewHolder.messageBody = (TextView) view
						.findViewById(R.id.message_body);
				viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
				view.setTag(viewHolder);
				break;
			case RECIEVED:
				view = (View) activity.getLayoutInflater().inflate(
						R.layout.message_recieved, parent, false);
				viewHolder.message_box = (LinearLayout) view
						.findViewById(R.id.message_box);
				viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);

				viewHolder.download_button = (Button) view
						.findViewById(R.id.download_button);

				if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

					viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
							item.getConversation().getContact(), getContext()));

				}
				viewHolder.indicator = (ImageView) view
						.findViewById(R.id.security_indicator);
				viewHolder.image = (ImageView) view
						.findViewById(R.id.message_image);
				viewHolder.messageBody = (TextView) view
						.findViewById(R.id.message_body);
				viewHolder.time = (TextView) view
						.findViewById(R.id.message_time);
				view.setTag(viewHolder);
				break;
			case STATUS:
				view = (View) activity.getLayoutInflater().inflate(
						R.layout.message_status, parent, false);
				viewHolder.contact_picture = (ImageView) view
						.findViewById(R.id.message_photo);
				if (item.getConversation().getMode() == Conversation.MODE_SINGLE) {

					viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
							item.getConversation().getContact(), getContext()));
					viewHolder.contact_picture.setAlpha(128);
					viewHolder.contact_picture
							.setOnClickListener(new OnClickListener() {

								@Override
								public void onClick(View v) {
									String name = item.getConversation()
											.getName(true);
									String read = getContext()
											.getString(
													R.string.contact_has_read_up_to_this_point,
													name);
									Toast.makeText(getContext(), read,
											Toast.LENGTH_SHORT).show();
								}
							});

				}
				break;
			default:
				viewHolder = null;
				break;
			}
		} else {
			viewHolder = (ViewHolder) view.getTag();
		}

		if (type == STATUS) {
			return view;
		}

		if (type == RECIEVED) {
			if (item.getConversation().getMode() == Conversation.MODE_MULTI) {
				Contact contact = item.getContact();
				if (contact != null) {
					viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(
							contact, getContext()));
				} else {
					String name = item.getPresence();
					if (name==null) {
						name = item.getCounterpart();
					}
					viewHolder.contact_picture.setImageBitmap(mBitmapCache.get(name, getContext()));
				}
				viewHolder.contact_picture
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								if (MessageAdapter.this.mOnContactPictureClickedListener != null) {
									MessageAdapter.this.mOnContactPictureClickedListener
											.onContactPictureClicked(item);
									;
								}

							}
						});
				viewHolder.contact_picture.setOnLongClickListener(new OnLongClickListener() {
					
					@Override
					public boolean onLongClick(View v) {
						if (MessageAdapter.this.mOnContactPictureLongClickedListener != null) {
							MessageAdapter.this.mOnContactPictureLongClickedListener.onContactPictureLongClicked(item);
							return true;
						} else {
							return false;
						}
					}
				});
			}
		}

		if (item.getType() == Message.TYPE_IMAGE) {
			if (item.getStatus() == Message.STATUS_RECIEVING) {
				displayInfoMessage(viewHolder, R.string.receiving_image);
			} else if (item.getStatus() == Message.STATUS_RECEIVED_OFFER) {
				viewHolder.image.setVisibility(View.GONE);
				viewHolder.messageBody.setVisibility(View.GONE);
				viewHolder.download_button.setVisibility(View.VISIBLE);
				viewHolder.download_button
						.setOnClickListener(new OnClickListener() {

							@Override
							public void onClick(View v) {
								JingleConnection connection = item
										.getJingleConnection();
								if (connection != null) {
									connection.accept();
								}
							}
						});
			} else if ((item.getEncryption() == Message.ENCRYPTION_DECRYPTED)
					|| (item.getEncryption() == Message.ENCRYPTION_NONE)
					|| (item.getEncryption() == Message.ENCRYPTION_OTR)) {
				displayImageMessage(viewHolder, item);
			} else if (item.getEncryption() == Message.ENCRYPTION_PGP) {
				displayInfoMessage(viewHolder, R.string.encrypted_message);
			} else {
				displayDecryptionFailed(viewHolder);
			}
		} else {
			if (item.getEncryption() == Message.ENCRYPTION_PGP) {
				if (activity.hasPgp()) {
					displayInfoMessage(viewHolder, R.string.encrypted_message);
				} else {
					displayInfoMessage(viewHolder,
							R.string.install_openkeychain);
					viewHolder.message_box
							.setOnClickListener(new OnClickListener() {

								@Override
								public void onClick(View v) {
									activity.showInstallPgpDialog();
								}
							});
				}
			} else if (item.getEncryption() == Message.ENCRYPTION_DECRYPTION_FAILED) {
				displayDecryptionFailed(viewHolder);
			} else {
				displayTextMessage(viewHolder, item);
			}
		}

		displayStatus(viewHolder, item);

		return view;
	}

	private static class ViewHolder {

		protected LinearLayout message_box;
		protected Button download_button;
		protected ImageView image;
		protected ImageView indicator;
		protected TextView time;
		protected TextView messageBody;
		protected ImageView contact_picture;

	}

	private class BitmapCache {
		private HashMap<String, Bitmap> contactBitmaps = new HashMap<String, Bitmap>();
		private HashMap<String, Bitmap> unknownBitmaps = new HashMap<String, Bitmap>();

		public Bitmap get(Contact contact, Context context) {
			if (!contactBitmaps.containsKey(contact.getJid())) {
				contactBitmaps.put(contact.getJid(),
						contact.getImage(48, context));
			}
			return contactBitmaps.get(contact.getJid());
		}

		public Bitmap get(String name, Context context) {
			if (unknownBitmaps.containsKey(name)) {
				return unknownBitmaps.get(name);
			} else {
				Bitmap bm = UIHelper
						.getContactPicture(name, 48, context, false);
				unknownBitmaps.put(name, bm);
				return bm;
			}
		}
	}

	public interface OnContactPictureClicked {
		public void onContactPictureClicked(Message message);
	}
	
	public interface OnContactPictureLongClicked {
		public void onContactPictureLongClicked(Message message);
	}
}
