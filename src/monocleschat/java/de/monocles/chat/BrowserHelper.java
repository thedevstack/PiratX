package de.monocles.chat;

import android.content.Intent;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.content.ActivityNotFoundException;
import android.widget.Toast;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import androidx.browser.customtabs.CustomTabsIntent;

import eu.siacs.conversations.R;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.ui.XmppActivity;

public class BrowserHelper {
	static boolean launchNativeApi30(Context context, Uri uri) {
		Intent nativeAppIntent = new Intent(Intent.ACTION_VIEW, uri)
				.addCategory(Intent.CATEGORY_BROWSABLE)
				.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
						Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER);
		try {
			context.startActivity(nativeAppIntent);
			return true;
		} catch (ActivityNotFoundException ex) {
			return false;
		}
	}

	private static Set<String> extractPackageNames(List<ResolveInfo> infos) {
		Set<String> names = new HashSet<>();
		for (ResolveInfo resolveInfo : infos) {
			String packageName = resolveInfo.activityInfo.packageName;
			names.add(packageName);
		}
		return names;
	}

	private static boolean launchNativeBeforeApi30(Context context, Uri uri) {
		PackageManager pm = context.getPackageManager();

		// Get all Apps that resolve a generic url
		Intent browserActivityIntent = new Intent()
				.setAction(Intent.ACTION_VIEW)
				.addCategory(Intent.CATEGORY_BROWSABLE)
				.setData(Uri.fromParts("http", "", null));
		Set<String> genericResolvedList = extractPackageNames(
				pm.queryIntentActivities(browserActivityIntent, 0));

		// Get all apps that resolve the specific Url
		Intent specializedActivityIntent = new Intent(Intent.ACTION_VIEW, uri)
				.addCategory(Intent.CATEGORY_BROWSABLE);
		Set<String> resolvedSpecializedList = extractPackageNames(
				pm.queryIntentActivities(specializedActivityIntent, 0));

		// Keep only the Urls that resolve the specific, but not the generic
		// urls.
		resolvedSpecializedList.removeAll(genericResolvedList);

		// If the list is empty, no native app handlers were found.
		if (resolvedSpecializedList.isEmpty()) {
			return false;
		}

		// We found native handlers. Launch the Intent.
		specializedActivityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(specializedActivityIntent);
		return true;
	}

	public static void launchUri(Context context, Uri uri) {
		boolean launched = Build.VERSION.SDK_INT >= 30 ?
				launchNativeApi30(context, uri) :
				launchNativeBeforeApi30(context, uri);

      final var custom_tab = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("custom_tab", context.getResources().getBoolean(R.bool.default_custom_tab));
		if (!custom_tab) {
			try {
				final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
				context.startActivity(intent);
				return;
			} catch (ActivityNotFoundException e) {
			    Toast.makeText(context, R.string.no_application_found_to_open_link, Toast.LENGTH_SHORT).show();
			}
		}

		if (!launched) {
			var builder = new CustomTabsIntent.Builder()
				.setShowTitle(true)
				.setShareState(CustomTabsIntent.SHARE_STATE_ON)
				.setBackgroundInteractionEnabled(true)
				.setStartAnimations(context, R.anim.slide_in_right, R.anim.slide_out_left)
				.setExitAnimations(context, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
				.setCloseButtonIcon(FileBackend.drawDrawable(context.getDrawable(R.drawable.ic_arrow_back_24dp)))
				.setCloseButtonPosition(CustomTabsIntent.CLOSE_BUTTON_POSITION_START);
			if (context instanceof XmppActivity) {
				builder = builder.setColorScheme(((XmppActivity) context).isDark() ? CustomTabsIntent.COLOR_SCHEME_DARK : CustomTabsIntent.COLOR_SCHEME_LIGHT);
			}
			builder.build().launchUrl(context, uri);
		}
	}
}
