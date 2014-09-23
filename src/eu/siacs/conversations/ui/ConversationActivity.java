package eu.siacs.conversations.ui;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.UIHelper;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.IntentSender.SendIntentException;
import android.content.Intent;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

public class ConversationActivity extends XmppActivity implements
		OnAccountUpdate, OnConversationUpdate, OnRosterUpdate {

	public static final String VIEW_CONVERSATION = "viewConversation";
	public static final String CONVERSATION = "conversationUuid";
	public static final String TEXT = "text";
	public static final String PRESENCE = "eu.siacs.conversations.presence";

	public static final int REQUEST_SEND_MESSAGE = 0x0201;
	public static final int REQUEST_DECRYPT_PGP = 0x0202;
	private static final int REQUEST_ATTACH_FILE_DIALOG = 0x0203;
	private static final int REQUEST_IMAGE_CAPTURE = 0x0204;
	private static final int REQUEST_RECORD_AUDIO = 0x0205;
	private static final int REQUEST_SEND_PGP_IMAGE = 0x0206;
	public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;

	private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
	private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
	private static final int ATTACHMENT_CHOICE_RECORD_VOICE = 0x0303;

	protected SlidingPaneLayout spl;

	private List<Conversation> conversationList = new ArrayList<Conversation>();
	private Conversation selectedConversation = null;
	private ListView listView;

	private boolean paneShouldBeOpen = true;
	private ArrayAdapter<Conversation> listAdapter;

	protected ConversationActivity activity = this;
	private Toast prepareImageToast;

	private Uri pendingImageUri = null;

	public List<Conversation> getConversationList() {
		return this.conversationList;
	}

	public Conversation getSelectedConversation() {
		return this.selectedConversation;
	}

	public void setSelectedConversation(Conversation conversation) {
		this.selectedConversation = conversation;
	}

	public ListView getConversationListView() {
		return this.listView;
	}

	public SlidingPaneLayout getSlidingPaneLayout() {
		return this.spl;
	}

	public boolean shouldPaneBeOpen() {
		return paneShouldBeOpen;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.fragment_conversations_overview);

		listView = (ListView) findViewById(R.id.list);

		getActionBar().setDisplayHomeAsUpEnabled(false);
		getActionBar().setHomeButtonEnabled(false);

		this.listAdapter = new ConversationAdapter(this, conversationList);
		listView.setAdapter(this.listAdapter);

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
					int position, long arg3) {
				paneShouldBeOpen = false;
				if (getSelectedConversation() != conversationList.get(position)) {
					setSelectedConversation(conversationList.get(position));
					swapConversationFragment();
				} else {
					spl.closePane();
				}
			}
		});
		spl = (SlidingPaneLayout) findViewById(R.id.slidingpanelayout);
		spl.setParallaxDistance(150);
		spl.setShadowResource(R.drawable.es_slidingpane_shadow);
		spl.setSliderFadeColor(0);
		spl.setPanelSlideListener(new PanelSlideListener() {

			@Override
			public void onPanelOpened(View arg0) {
				paneShouldBeOpen = true;
				ActionBar ab = getActionBar();
				if (ab != null) {
					ab.setDisplayHomeAsUpEnabled(false);
					ab.setHomeButtonEnabled(false);
					ab.setTitle(R.string.app_name);
				}
				invalidateOptionsMenu();
				hideKeyboard();
			}

			@Override
			public void onPanelClosed(View arg0) {
				paneShouldBeOpen = false;
				if ((conversationList.size() > 0)
						&& (getSelectedConversation() != null)) {
					ActionBar ab = getActionBar();
					if (ab != null) {
						ab.setDisplayHomeAsUpEnabled(true);
						ab.setHomeButtonEnabled(true);
						if (getSelectedConversation().getMode() == Conversation.MODE_SINGLE
								|| activity.useSubjectToIdentifyConference()) {
							ab.setTitle(getSelectedConversation().getName());
						} else {
							ab.setTitle(getSelectedConversation()
									.getContactJid().split("/")[0]);
						}
					}
					invalidateOptionsMenu();
					if (!getSelectedConversation().isRead()) {
						xmppConnectionService
								.markRead(getSelectedConversation());
						UIHelper.updateNotification(getApplicationContext(),
								getConversationList(), null, false);
						listView.invalidateViews();
					}
				}
			}

			@Override
			public void onPanelSlide(View arg0, float arg1) {
				// TODO Auto-generated method stub

			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.conversations, menu);
		MenuItem menuSecure = (MenuItem) menu.findItem(R.id.action_security);
		MenuItem menuArchive = (MenuItem) menu.findItem(R.id.action_archive);
		MenuItem menuMucDetails = (MenuItem) menu
				.findItem(R.id.action_muc_details);
		MenuItem menuContactDetails = (MenuItem) menu
				.findItem(R.id.action_contact_details);
		MenuItem menuAttach = (MenuItem) menu.findItem(R.id.action_attach_file);
		MenuItem menuClearHistory = (MenuItem) menu
				.findItem(R.id.action_clear_history);
		MenuItem menuAdd = (MenuItem) menu.findItem(R.id.action_add);
		MenuItem menuInviteContact = (MenuItem) menu
				.findItem(R.id.action_invite);
		MenuItem menuMute = (MenuItem) menu.findItem(R.id.action_mute);

		if ((spl.isOpen() && (spl.isSlideable()))) {
			menuArchive.setVisible(false);
			menuMucDetails.setVisible(false);
			menuContactDetails.setVisible(false);
			menuSecure.setVisible(false);
			menuInviteContact.setVisible(false);
			menuAttach.setVisible(false);
			menuClearHistory.setVisible(false);
			menuMute.setVisible(false);
		} else {
			menuAdd.setVisible(!spl.isSlideable());
			if (this.getSelectedConversation() != null) {
				if (this.getSelectedConversation().getLatestMessage()
						.getEncryption() != Message.ENCRYPTION_NONE) {
					menuSecure.setIcon(R.drawable.ic_action_secure);
				}
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					menuContactDetails.setVisible(false);
					menuAttach.setVisible(false);
				} else {
					menuMucDetails.setVisible(false);
					menuInviteContact.setVisible(false);
				}
			}
		}
		return true;
	}

	private void selectPresenceToAttachFile(final int attachmentChoice) {
		selectPresence(getSelectedConversation(), new OnPresenceSelected() {

			@Override
			public void onPresenceSelected() {
				if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO) {
					pendingImageUri = xmppConnectionService.getFileBackend()
							.getTakePhotoUri();
					Intent takePictureIntent = new Intent(
							MediaStore.ACTION_IMAGE_CAPTURE);
					takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
							pendingImageUri);
					if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
						startActivityForResult(takePictureIntent,
								REQUEST_IMAGE_CAPTURE);
					}
				} else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
					Intent attachFileIntent = new Intent();
					attachFileIntent.setType("image/*");
					attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
					Intent chooser = Intent.createChooser(attachFileIntent,
							getString(R.string.attach_file));
					startActivityForResult(chooser, REQUEST_ATTACH_FILE_DIALOG);
				} else if (attachmentChoice == ATTACHMENT_CHOICE_RECORD_VOICE) {
					Intent intent = new Intent(
							MediaStore.Audio.Media.RECORD_SOUND_ACTION);
					startActivityForResult(intent, REQUEST_RECORD_AUDIO);
				}
			}
		});
	}

	private void attachFile(final int attachmentChoice) {
		final Conversation conversation = getSelectedConversation();
		if (conversation.getNextEncryption(forceEncryption()) == Message.ENCRYPTION_PGP) {
			if (hasPgp()) {
				if (conversation.getContact().getPgpKeyId() != 0) {
					xmppConnectionService.getPgpEngine().hasKey(
							conversation.getContact(),
							new UiCallback<Contact>() {

								@Override
								public void userInputRequried(PendingIntent pi,
										Contact contact) {
									ConversationActivity.this.runIntent(pi,
											attachmentChoice);
								}

								@Override
								public void success(Contact contact) {
									selectPresenceToAttachFile(attachmentChoice);
								}

								@Override
								public void error(int error, Contact contact) {
									displayErrorDialog(error);
								}
							});
				} else {
					final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
							.findFragmentByTag("conversation");
					if (fragment != null) {
						fragment.showNoPGPKeyDialog(false,
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										conversation
												.setNextEncryption(Message.ENCRYPTION_NONE);
										selectPresenceToAttachFile(attachmentChoice);
									}
								});
					}
				}
			} else {
				showInstallPgpDialog();
			}
		} else if (getSelectedConversation().getNextEncryption(
				forceEncryption()) == Message.ENCRYPTION_NONE) {
			selectPresenceToAttachFile(attachmentChoice);
		} else {
			selectPresenceToAttachFile(attachmentChoice);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			spl.openPane();
			return true;
		} else if (item.getItemId() == R.id.action_add) {
			startActivity(new Intent(this, StartConversationActivity.class));
			return true;
		} else if (getSelectedConversation() != null) {
			switch (item.getItemId()) {
			case R.id.action_attach_file:
				attachFileDialog();
				break;
			case R.id.action_archive:
				this.endConversation(getSelectedConversation());
				break;
			case R.id.action_contact_details:
				Contact contact = this.getSelectedConversation().getContact();
				if (contact.showInRoster()) {
					switchToContactDetails(contact);
				} else {
					showAddToRosterDialog(getSelectedConversation());
				}
				break;
			case R.id.action_muc_details:
				Intent intent = new Intent(this,
						ConferenceDetailsActivity.class);
				intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
				intent.putExtra("uuid", getSelectedConversation().getUuid());
				startActivity(intent);
				break;
			case R.id.action_invite:
				inviteToConversation(getSelectedConversation());
				break;
			case R.id.action_security:
				selectEncryptionDialog(getSelectedConversation());
				break;
			case R.id.action_clear_history:
				clearHistoryDialog(getSelectedConversation());
				break;
			case R.id.action_mute:
				muteConversationDialog(getSelectedConversation());
				break;
			default:
				break;
			}
			return super.onOptionsItemSelected(item);
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	public void endConversation(Conversation conversation) {
		conversation.setStatus(Conversation.STATUS_ARCHIVED);
		paneShouldBeOpen = true;
		spl.openPane();
		xmppConnectionService.archiveConversation(conversation);
		if (conversationList.size() > 0) {
			setSelectedConversation(conversationList.get(0));
		} else {
			setSelectedConversation(null);
		}
	}

	protected void clearHistoryDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.clear_conversation_history));
		View dialogView = getLayoutInflater().inflate(
				R.layout.dialog_clear_history, null);
		final CheckBox endConversationCheckBox = (CheckBox) dialogView
				.findViewById(R.id.end_conversation_checkbox);
		builder.setView(dialogView);
		builder.setNegativeButton(getString(R.string.cancel), null);
		builder.setPositiveButton(getString(R.string.delete_messages),
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						activity.xmppConnectionService
								.clearConversationHistory(conversation);
						if (endConversationCheckBox.isChecked()) {
							endConversation(conversation);
						}
					}
				});
		builder.create().show();
	}
	
	protected void attachFileDialog() {
		View menuAttachFile = findViewById(R.id.action_attach_file);
		if (menuAttachFile == null) {
			return;
		}
		PopupMenu attachFilePopup = new PopupMenu(this, menuAttachFile);
		attachFilePopup.inflate(R.menu.attachment_choices);
		attachFilePopup
				.setOnMenuItemClickListener(new OnMenuItemClickListener() {

					@Override
					public boolean onMenuItemClick(MenuItem item) {
						switch (item.getItemId()) {
						case R.id.attach_choose_picture:
							attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
							break;
						case R.id.attach_take_picture:
							attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
							break;
						case R.id.attach_record_voice:
							attachFile(ATTACHMENT_CHOICE_RECORD_VOICE);
							break;
						}
						return false;
					}
				});
		attachFilePopup.show();
	}

	protected void selectEncryptionDialog(final Conversation conversation) {
		View menuItemView = findViewById(R.id.action_security);
		if (menuItemView == null) {
			return;
		}
		PopupMenu popup = new PopupMenu(this, menuItemView);
		final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
				.findFragmentByTag("conversation");
		if (fragment != null) {
			popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

				@Override
				public boolean onMenuItemClick(MenuItem item) {
					switch (item.getItemId()) {
					case R.id.encryption_choice_none:
						conversation.setNextEncryption(Message.ENCRYPTION_NONE);
						item.setChecked(true);
						break;
					case R.id.encryption_choice_otr:
						conversation.setNextEncryption(Message.ENCRYPTION_OTR);
						item.setChecked(true);
						break;
					case R.id.encryption_choice_pgp:
						if (hasPgp()) {
							if (conversation.getAccount().getKeys()
									.has("pgp_signature")) {
								conversation
										.setNextEncryption(Message.ENCRYPTION_PGP);
								item.setChecked(true);
							} else {
								announcePgp(conversation.getAccount(),
										conversation);
							}
						} else {
							showInstallPgpDialog();
						}
						break;
					default:
						conversation.setNextEncryption(Message.ENCRYPTION_NONE);
						break;
					}
					fragment.updateChatMsgHint();
					return true;
				}
			});
			popup.inflate(R.menu.encryption_choices);
			MenuItem otr = popup.getMenu().findItem(R.id.encryption_choice_otr);
			MenuItem none = popup.getMenu().findItem(
					R.id.encryption_choice_none);
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				otr.setEnabled(false);
			} else {
				if (forceEncryption()) {
					none.setVisible(false);
				}
			}
			switch (conversation.getNextEncryption(forceEncryption())) {
			case Message.ENCRYPTION_NONE:
				none.setChecked(true);
				break;
			case Message.ENCRYPTION_OTR:
				otr.setChecked(true);
				break;
			case Message.ENCRYPTION_PGP:
				popup.getMenu().findItem(R.id.encryption_choice_pgp)
						.setChecked(true);
				break;
			default:
				popup.getMenu().findItem(R.id.encryption_choice_none)
						.setChecked(true);
				break;
			}
			popup.show();
		}
	}

	protected void muteConversationDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.disable_notifications_for_this_conversation);
		final int[] durations = getResources().getIntArray(
				R.array.mute_options_durations);
		builder.setItems(R.array.mute_options_descriptions,
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						long till;
						if (durations[which] == -1) {
							till = Long.MAX_VALUE;
						} else {
							till = SystemClock.elapsedRealtime()
									+ (durations[which] * 1000);
						}
						conversation.setMutedTill(till);
						ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
								.findFragmentByTag("conversation");
						if (selectedFragment != null) {
							selectedFragment.updateMessages();
						}
					}
				});
		builder.create().show();
	}

	protected ConversationFragment swapConversationFragment() {
		ConversationFragment selectedFragment = new ConversationFragment();
		if (!isFinishing()) {

			FragmentTransaction transaction = getFragmentManager()
					.beginTransaction();
			transaction.replace(R.id.selected_conversation, selectedFragment,
					"conversation");

			transaction.commitAllowingStateLoss();
		}
		return selectedFragment;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!spl.isOpen()) {
				spl.openPane();
				return false;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onNewIntent(Intent intent) {
		if (xmppConnectionServiceBound) {
			if ((Intent.ACTION_VIEW.equals(intent.getAction()) && (VIEW_CONVERSATION
					.equals(intent.getType())))) {
				String convToView = (String) intent.getExtras().get(
						CONVERSATION);
				updateConversationList();
				for (int i = 0; i < conversationList.size(); ++i) {
					if (conversationList.get(i).getUuid().equals(convToView)) {
						setSelectedConversation(conversationList.get(i));
						break;
					}
				}
				paneShouldBeOpen = false;
				String text = intent.getExtras().getString(TEXT, null);
				swapConversationFragment().setText(text);
			}
		} else {
			handledViewIntent = false;
			setIntent(intent);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (this.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
		if (conversationList.size() >= 1) {
			this.onConversationUpdate();
		}
	}

	@Override
	protected void onStop() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.removeOnConversationListChangedListener();
			xmppConnectionService.removeOnAccountListChangedListener();
			xmppConnectionService.removeOnRosterUpdateListener();
		}
		super.onStop();
	}

	@Override
	void onBackendConnected() {
		this.registerListener();
		if (conversationList.size() == 0) {
			updateConversationList();
		}

		if (getSelectedConversation() != null && pendingImageUri != null) {
			attachImageToConversation(getSelectedConversation(),
					pendingImageUri);
			pendingImageUri = null;
		} else {
			pendingImageUri = null;
		}

		if ((getIntent().getAction() != null)
				&& (getIntent().getAction().equals(Intent.ACTION_VIEW) && (!handledViewIntent))) {
			if (getIntent().getType().equals(
					ConversationActivity.VIEW_CONVERSATION)) {
				handledViewIntent = true;

				String convToView = (String) getIntent().getExtras().get(
						CONVERSATION);

				for (int i = 0; i < conversationList.size(); ++i) {
					if (conversationList.get(i).getUuid().equals(convToView)) {
						setSelectedConversation(conversationList.get(i));
					}
				}
				paneShouldBeOpen = false;
				String text = getIntent().getExtras().getString(TEXT, null);
				swapConversationFragment().setText(text);
			}
		} else {
			if (xmppConnectionService.getAccounts().size() == 0) {
				startActivity(new Intent(this, EditAccountActivity.class));
			} else if (conversationList.size() <= 0) {
				// add no history
				startActivity(new Intent(this, StartConversationActivity.class));
				finish();
			} else {
				spl.openPane();
				// find currently loaded fragment
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
						.findFragmentByTag("conversation");
				if (selectedFragment != null) {
					selectedFragment.onBackendConnected();
				} else {
					setSelectedConversation(conversationList.get(0));
					swapConversationFragment();
				}
				ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
			}
		}
	}

	public void registerListener() {
		if (xmppConnectionServiceBound) {
			xmppConnectionService.setOnConversationListChangedListener(this);
			xmppConnectionService.setOnAccountListChangedListener(this);
			xmppConnectionService.setOnRosterUpdateListener(this);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
			final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_DECRYPT_PGP) {
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
						.findFragmentByTag("conversation");
				if (selectedFragment != null) {
					selectedFragment.hideSnackbar();
				}
			} else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
				pendingImageUri = data.getData();
				if (xmppConnectionServiceBound) {
					attachImageToConversation(getSelectedConversation(),
							pendingImageUri);
					pendingImageUri = null;
				}
			} else if (requestCode == REQUEST_SEND_PGP_IMAGE) {

			} else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
				attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
			} else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
				attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
			} else if (requestCode == REQUEST_ANNOUNCE_PGP) {
				announcePgp(getSelectedConversation().getAccount(),
						getSelectedConversation());
			} else if (requestCode == REQUEST_ENCRYPT_MESSAGE) {
				// encryptTextMessage();
			} else if (requestCode == REQUEST_IMAGE_CAPTURE) {
				if (xmppConnectionServiceBound) {
					attachImageToConversation(getSelectedConversation(),
							pendingImageUri);
					pendingImageUri = null;
				}
				Intent intent = new Intent(
						Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				intent.setData(pendingImageUri);
				sendBroadcast(intent);
			} else if (requestCode == REQUEST_RECORD_AUDIO) {
				attachAudioToConversation(getSelectedConversation(),
						data.getData());
			}
		}
	}

	private void attachAudioToConversation(Conversation conversation, Uri uri) {

	}

	private void attachImageToConversation(Conversation conversation, Uri uri) {
		prepareImageToast = Toast.makeText(getApplicationContext(),
				getText(R.string.preparing_image), Toast.LENGTH_LONG);
		prepareImageToast.show();
		xmppConnectionService.attachImageToConversation(conversation, uri,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi,
							Message object) {
						hidePrepareImageToast();
						ConversationActivity.this.runIntent(pi,
								ConversationActivity.REQUEST_SEND_PGP_IMAGE);
					}

					@Override
					public void success(Message message) {
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(int error, Message message) {
						hidePrepareImageToast();
						displayErrorDialog(error);
					}
				});
	}

	private void hidePrepareImageToast() {
		if (prepareImageToast != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					prepareImageToast.cancel();
				}
			});
		}
	}

	public void updateConversationList() {
		xmppConnectionService
				.populateWithOrderedConversations(conversationList);
		listAdapter.notifyDataSetChanged();
	}

	public void runIntent(PendingIntent pi, int requestCode) {
		try {
			this.startIntentSenderForResult(pi.getIntentSender(), requestCode,
					null, 0, 0, 0);
		} catch (SendIntentException e1) {
		}
	}

	public void encryptTextMessage(Message message) {
		xmppConnectionService.getPgpEngine().encrypt(message,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi,
							Message message) {
						activity.runIntent(pi,
								ConversationActivity.REQUEST_SEND_MESSAGE);
					}

					@Override
					public void success(Message message) {
						message.setEncryption(Message.ENCRYPTION_DECRYPTED);
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(int error, Message message) {

					}
				});
	}

	public boolean forceEncryption() {
		return getPreferences().getBoolean("force_encryption", false);
	}

	public boolean useSendButtonToIndicateStatus() {
		return getPreferences().getBoolean("send_button_status", false);
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", false);
	}

	@Override
	public void onAccountUpdate() {
		final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
				.findFragmentByTag("conversation");
		if (fragment != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					fragment.updateMessages();
				}
			});
		}
	}

	@Override
	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateConversationList();
				if (paneShouldBeOpen) {
					if (conversationList.size() >= 1) {
						swapConversationFragment();
					} else {
						startActivity(new Intent(getApplicationContext(),
								StartConversationActivity.class));
						finish();
					}
				}
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
						.findFragmentByTag("conversation");
				if (selectedFragment != null) {
					selectedFragment.updateMessages();
				}
			}
		});
	}

	@Override
	public void onRosterUpdate() {
		final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
				.findFragmentByTag("conversation");
		if (fragment != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					fragment.updateMessages();
				}
			});
		}
	}
}
