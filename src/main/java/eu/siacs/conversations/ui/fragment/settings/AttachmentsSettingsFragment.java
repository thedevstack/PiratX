package eu.siacs.conversations.ui.fragment.settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;

import de.monocles.chat.DownloadDefaultStickers;

import eu.siacs.conversations.BuildConfig;
import eu.siacs.conversations.R;
import eu.siacs.conversations.utils.UIHelper;

public class AttachmentsSettingsFragment extends XmppPreferenceFragment {

    private final ActivityResultLauncher<String> requestStorageLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            downloadStickers();
                        } else {
                            Toast.makeText(
                                            requireActivity(),
                                            getString(
                                                    R.string.no_storage_permission,
                                                    getString(R.string.app_name)),
                                            Toast.LENGTH_LONG)
                                    .show();
                        }
                    });

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences_attachments, rootKey);

        final var p = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        final var stickerDir = findPreference("sticker_directory");
        if (Build.VERSION.SDK_INT >= 29) {
            stickerDir.setSummary(p.getString("sticker_directory", Environment.DIRECTORY_DOCUMENTS + "/" + BuildConfig.APP_NAME + "/Stickers"));
            stickerDir.setOnPreferenceClickListener((pref) -> {
                final var intent = ((StorageManager) requireActivity().getSystemService(Context.STORAGE_SERVICE)).getPrimaryStorageVolume().createOpenDocumentTreeIntent();
                startActivityForResult(Intent.createChooser(intent, "Choose sticker location"), 0);
                return true;
            });
        } else {
            stickerDir.setVisible(false);
        }

        final var downloadDefaultStickers = findPreference("download_default_stickers");
        downloadDefaultStickers.setOnPreferenceClickListener((pref) -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                } else {
                    downloadStickers();
                }
            } else {
                downloadStickers();
            }
            return true;
        });

        final var clearBlockedMedia = findPreference("clear_blocked_media");
        clearBlockedMedia.setOnPreferenceClickListener((pref) -> {
            requireService().clearBlockedMedia();
            runOnUiThread(() -> Toast.makeText(requireActivity(), "Blocked media will be displayed again", Toast.LENGTH_LONG).show());
            return true;
        });

        final ListPreference autoAcceptFileSize = findPreference("auto_accept_file_size");
        if (autoAcceptFileSize == null) {
            throw new IllegalStateException("The preference resource file is missing preferences");
        }
        setValues(
                autoAcceptFileSize,
                R.array.file_size_values,
                value -> {
                    if (value <= 0) {
                        return getString(R.string.never);
                    } else {
                        return UIHelper.filesizeToString(value);
                    }
                });
    }

    protected void downloadStickers() {
        final var intent = new Intent(requireActivity(), DownloadDefaultStickers.class);
        intent.putExtra("tor", requireService().useTorToConnect());
        intent.putExtra("i2p", requireService().useI2PToConnect());
        ContextCompat.startForegroundService(requireActivity(), intent);
        runOnUiThread(() -> Toast.makeText(requireActivity(), "Sticker download started", Toast.LENGTH_LONG).show());
    }

    @Override
    public void onStart() {
        super.onStart();
        requireActivity().setTitle(R.string.pref_attachments);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null || data.getData() == null) return;

        final var p = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        p.edit().putString("sticker_directory", data.getData().toString()).commit();
        final var stickerDir = findPreference("sticker_directory");
        stickerDir.setSummary(data.getData().toString());
    }
}
