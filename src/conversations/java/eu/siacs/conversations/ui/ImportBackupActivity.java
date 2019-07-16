package eu.siacs.conversations.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.io.IOException;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityImportBackupBinding;
import eu.siacs.conversations.databinding.DialogEnterPasswordBinding;
import eu.siacs.conversations.services.ImportBackupService;
import eu.siacs.conversations.ui.adapter.BackupFileAdapter;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.utils.ThemeHelper;

public class ImportBackupActivity extends ActionBarActivity implements ServiceConnection, ImportBackupService.OnBackupFilesLoaded, BackupFileAdapter.OnItemClickedListener, ImportBackupService.OnBackupProcessed {

    private ActivityImportBackupBinding binding;

    private BackupFileAdapter backupFileAdapter;
    private ImportBackupService service;

    private boolean mLoadingState = false;

    private int mTheme;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        this.mTheme = ThemeHelper.find(this);
        setTheme(this.mTheme);
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_import_backup);
        setSupportActionBar((Toolbar) binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.backupFileAdapter = new BackupFileAdapter();
        this.binding.list.setAdapter(this.backupFileAdapter);
        this.backupFileAdapter.setOnItemClickedListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.import_backup, menu);
        final MenuItem openBackup = menu.findItem(R.id.action_open_backup_file);
        openBackup.setVisible(!this.mLoadingState);
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        final int theme = ThemeHelper.find(this);
        if (this.mTheme != theme) {
            recreate();
        } else {
            bindService(new Intent(this, ImportBackupService.class), this, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.service != null) {
            this.service.removeOnBackupProcessedListener(this);
        }
        unbindService(this);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        ImportBackupService.ImportBackupServiceBinder binder = (ImportBackupService.ImportBackupServiceBinder) service;
        this.service = binder.getService();
        this.service.addOnBackupProcessedListener(this);
        setLoadingState(this.service.getLoadingState());
        this.service.loadBackupFiles(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        this.service = null;
    }

    @Override
    public void onBackupFilesLoaded(final List<ImportBackupService.BackupFile> files) {
        runOnUiThread(() -> backupFileAdapter.setFiles(files));
    }

    @Override
    public void onClick(final ImportBackupService.BackupFile backupFile) {
        showEnterPasswordDialog(backupFile);
    }

    private void openBackupFileFromUri(final Uri uri) {
        try {
            final ImportBackupService.BackupFile backupFile = ImportBackupService.BackupFile.read(this, uri);
            showEnterPasswordDialog(backupFile);
        } catch (IOException e) {
            Snackbar.make(binding.coordinator, R.string.not_a_backup_file, Snackbar.LENGTH_LONG).show();
        }
    }

    private void showEnterPasswordDialog(final ImportBackupService.BackupFile backupFile) {
        final DialogEnterPasswordBinding enterPasswordBinding = DataBindingUtil.inflate(LayoutInflater.from(this), R.layout.dialog_enter_password, null, false);
        Log.d(Config.LOGTAG, "attempting to import " + backupFile.getUri());
        enterPasswordBinding.explain.setText(getString(R.string.enter_password_to_restore, backupFile.getHeader().getJid().toString()));
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(enterPasswordBinding.getRoot());
        builder.setTitle(R.string.enter_password);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.restore, (dialog, which) -> {
            final String password = enterPasswordBinding.accountPassword.getEditableText().toString();
            final Uri uri = backupFile.getUri();
            Intent intent = new Intent(this, ImportBackupService.class);
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra("password", password);
            if ("file".equals(uri.getScheme())) {
                intent.putExtra("file", uri.getPath());
            } else {
                intent.setData(uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            setLoadingState(true);
            ContextCompat.startForegroundService(this, intent);
        });
        builder.setCancelable(false);
        builder.create().show();
    }

    private void setLoadingState(final boolean loadingState) {
        binding.coordinator.setVisibility(loadingState ? View.GONE : View.VISIBLE);
        binding.inProgress.setVisibility(loadingState ? View.VISIBLE : View.GONE);
        setTitle(loadingState ? R.string.restoring_backup : R.string.restore_backup);
        configureActionBar(getSupportActionBar(), !loadingState);
        this.mLoadingState = loadingState;
        invalidateOptionsMenu();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            if (requestCode == 0xbac) {
                openBackupFileFromUri(intent.getData());
            }
        }
    }

    @Override
    public void onAccountAlreadySetup() {
        runOnUiThread(() -> {
            setLoadingState(false);
            Snackbar.make(binding.coordinator, R.string.account_already_setup, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackupRestored() {
        runOnUiThread(() -> {
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    @Override
    public void onBackupDecryptionFailed() {
        runOnUiThread(() -> {
            setLoadingState(false);
            Snackbar.make(binding.coordinator, R.string.unable_to_decrypt_backup, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public void onBackupRestoreFailed() {
        runOnUiThread(() -> {
            setLoadingState(false);
            Snackbar.make(binding.coordinator, R.string.unable_to_restore_backup, Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_open_backup_file:
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
                }
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(intent, getString(R.string.open_backup)), 0xbac);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
