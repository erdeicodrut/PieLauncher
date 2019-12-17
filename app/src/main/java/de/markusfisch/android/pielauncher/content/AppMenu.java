package de.markusfisch.android.pielauncher.content;

import de.markusfisch.android.pielauncher.graphics.CanvasPieMenu;
import de.markusfisch.android.pielauncher.graphics.Converter;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Process;
import android.os.UserHandle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AppMenu extends CanvasPieMenu {
	public static class AppIcon extends CanvasPieMenu.CanvasIcon {
		public final ComponentName componentName;
		public final String label;
		public final Drawable icon;

		AppIcon(ComponentName componentName, String label, Drawable icon) {
			super(Converter.getBitmapFromDrawable(icon));
			this.componentName = componentName;
			this.label = label;
			this.icon = icon;
		}
	}

	public interface UpdateListener {
		void onUpdate();
	}

	private static boolean HAS_LAUNCHER_APP =
			Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
	private static final String MENU = "menu";
	private static final Comparator<AppIcon> appLabelComparator = new Comparator<AppIcon>() {
		public int compare(AppIcon left, AppIcon right) {
			return left.label.compareToIgnoreCase(right.label);
		}
	};

	private final HashMap<ComponentName, AppIcon> apps = new HashMap<>();

	private UpdateListener updateListener;
	private UserHandle userHandle;
	private LauncherApps launcherApps;

	public void launchApp(Context context) {
		int selectedIcon = getSelectedIcon();
		if (selectedIcon > -1) {
			launchApp(context, ((AppIcon) icons.get(selectedIcon)));
		}
	}

	public void launchApp(Context context, AppIcon icon) {
		if (HAS_LAUNCHER_APP) {
			launcherApps.startMainActivity(
					icon.componentName,
					userHandle,
					icon.dst,
					null);
		} else {
			PackageManager pm = context.getPackageManager();
			Intent intent;
			if (pm == null || (intent = pm.getLaunchIntentForPackage(
					icon.componentName.getPackageName())) == null) {
				return;
			}
			context.startActivity(intent);
		}
	}

	public void setUpdateListener(UpdateListener listener) {
		updateListener = listener;
	}

	public void store(Context context) {
		storeMenu(context, icons);
	}

	public List<AppIcon> filterAppsBy(String query) {
		if (query == null) {
			query = "";
		}
		query = query.trim().toLowerCase(Locale.US);
		ArrayList<AppIcon> list = new ArrayList<>();
		if (query.length() < 1) {
			list.addAll(apps.values());
		} else {
			for (Map.Entry entry : apps.entrySet()) {
				AppIcon appIcon = (AppIcon) entry.getValue();
				if (appIcon.label.toLowerCase(Locale.US).contains(query)) {
					list.add(appIcon);
				}
			}
		}
		Collections.sort(list, appLabelComparator);
		return list;
	}

	// this AsyncTask is running for a short and finite time only
	// and it's perfectly okay to delay garbage collection of the
	// parent instance until this task has been terminated
	@SuppressLint("StaticFieldLeak")
	public void indexAppsAsync(Context context) {
		// get application context to not block garbage collection
		// on other Context objects
		final Context appContext = context.getApplicationContext();
		if (HAS_LAUNCHER_APP) {
			userHandle = Process.myUserHandle();
			launcherApps = (LauncherApps) appContext.getSystemService(
				Context.LAUNCHER_APPS_SERVICE);
		}
		new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... nothing) {
				indexApps(appContext);
				return null;
			}

			@Override
			protected void onPostExecute(Void nothing) {
				if (updateListener != null) {
					updateListener.onUpdate();
				}
			}
		}.execute();
	}

	private void indexApps(Context context) {
		apps.clear();
		Intent intent = new Intent(Intent.ACTION_MAIN, null);
		intent.addCategory(Intent.CATEGORY_LAUNCHER);
		PackageManager pm = context.getPackageManager();
		List<ResolveInfo> activities = pm.queryIntentActivities(intent, 0);
		if (activities == null) {
			return;
		}
		String skip = context.getPackageName();
		for (ResolveInfo info : activities) {
			String pn = info.activityInfo.applicationInfo.packageName;
			if (skip.equals(pn)) {
				continue;
			}
			if (HAS_LAUNCHER_APP) {
				for (LauncherActivityInfo ai : launcherApps.getActivityList(
						pn, userHandle)) {
					addApp(ai.getComponentName(),
							ai.getLabel().toString(),
							ai.getBadgedIcon(0));
				}
			} else {
				addApp(getComponentName(info),
						info.loadLabel(pm).toString(),
						info.loadIcon(pm));
			}
		}
		createIcons(context);
	}

	private void addApp(ComponentName componentName, String label,
			Drawable icon) {
		apps.put(componentName, new AppIcon(componentName, label, icon));
	}

	private void createIcons(Context context) {
		icons.clear();
		icons.addAll(restoreMenu(context, apps));
		if (icons.isEmpty()) {
			createInitialMenu(context.getPackageManager());
		}
	}

	private void createInitialMenu(PackageManager pm) {
		Intent[] intents = new Intent[]{
				new Intent(Intent.ACTION_VIEW, Uri.parse("http://")),
				new Intent(Intent.ACTION_DIAL),
				new Intent(Intent.ACTION_SENDTO, Uri.parse("sms:")),
				new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
				new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")),
				new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
		};
		ArrayList<String> defaults = new ArrayList<>();
		for (Intent intent : intents) {
			String packageName = resolveDefaultAppForIntent(pm, intent);
			if (packageName == null || defaults.contains(packageName)) {
				continue;
			}
			// get launch intent because the class name from above intents
			// doesn't match the launch intent and so doesn't match the
			// ComponentName key in apps
			Intent launchIntent = pm.getLaunchIntentForPackage(packageName);
			if (launchIntent == null) {
				continue;
			}
			AppIcon appIcon = apps.get(launchIntent.getComponent());
			if (appIcon != null) {
				defaults.add(packageName);
				addAppIcon(appIcon);
			}
		}
		int max = Math.min(apps.size(), 6);
		int i = icons.size();
		for (Map.Entry entry : apps.entrySet()) {
			if (i >= max) {
				break;
			}
			if (!defaults.contains(
					((ComponentName) entry.getKey()).getPackageName())) {
				addAppIcon((AppIcon) entry.getValue());
				++i;
			}
		}
	}

	private static String resolveDefaultAppForIntent(PackageManager pm,
			Intent intent) {
		ResolveInfo resolveInfo = pm.resolveActivity(intent,
				PackageManager.MATCH_DEFAULT_ONLY);
		return resolveInfo != null ?
				resolveInfo.activityInfo.packageName : null;
	}

	private void addAppIcon(AppIcon appIcon) {
		if (appIcon != null) {
			icons.add(appIcon);
		}
	}

	private static ComponentName getComponentName(ResolveInfo info) {
		return new ComponentName(info.activityInfo.packageName,
				info.activityInfo.name);
	}

	private static List<Icon> restoreMenu(Context context,
			HashMap<ComponentName, AppIcon> apps) {
		ArrayList<Icon> icons = new ArrayList<>();
		try {
			for (String line : readLines(context.openFileInput(MENU))) {
				ComponentName componentName =
						ComponentName.unflattenFromString(line);
				// because the first version saved only package names;
				// subject to be removed a after update!
				if (componentName == null) {
					PackageManager pm = context.getPackageManager();
					Intent launchIntent = pm.getLaunchIntentForPackage(line);
					if (launchIntent == null) {
						continue;
					}
					componentName = launchIntent.getComponent();
				}
				Icon icon = apps.get(componentName);
				if (icon != null) {
					icons.add(icon);
				}
			}
		} catch (FileNotFoundException e) {
			// return empty array
		}
		return icons;
	}

	private static List<String> readLines(InputStream is) {
		ArrayList<String> list = new ArrayList<>();
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
			while (reader.ready()) {
				list.add(reader.readLine());
			}
		} catch (IOException e) {
			// return what we got so far
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				// ignore, can't do anything about it
			}
		}
		return list;
	}

	private static boolean storeMenu(Context context, List<Icon> icons) {
		ArrayList<String> items = new ArrayList<>();
		for (CanvasPieMenu.Icon icon : icons) {
			items.add(((AppIcon) icon).componentName.flattenToString());
		}
		try {
			return writeLines(context.openFileOutput(MENU,
					Context.MODE_PRIVATE), items);
		} catch (IOException e) {
			return false;
		}
	}

	private static boolean writeLines(OutputStream os, List<String> lines) {
		if (os == null) {
			return false;
		}
		try {
			byte[] lf = "\n".getBytes("UTF-8");
			for (String line : lines) {
				os.write(line.getBytes("UTF-8"));
				os.write(lf);
			}
			return true;
		} catch (IOException e) {
			return false;
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				// ignore, can't do anything about it
			}
		}
	}
}
