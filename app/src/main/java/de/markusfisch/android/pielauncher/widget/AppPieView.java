package de.markusfisch.android.pielauncher.widget;

import de.markusfisch.android.pielauncher.content.AppMenu;
import de.markusfisch.android.pielauncher.graphics.Converter;
import de.markusfisch.android.pielauncher.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.net.Uri;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

public class AppPieView extends SurfaceView {
	public interface OpenListListener {
		void onOpenList();
	}

	public static final AppMenu appMenu = new AppMenu();

	private final ArrayList<AppMenu.Icon> iconsBeforeEdit = new ArrayList<>();
	private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Paint selectedPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
	private final Point touch = new Point();
	private final Point lastTouch = new Point();
	private final SurfaceHolder surfaceHolder;
	private final Rect iconAddRect = new Rect();
	private final Bitmap iconAdd;
	private final Rect iconRemoveRect = new Rect();
	private final Bitmap iconRemove;
	private final Rect iconInfoRect = new Rect();
	private final Bitmap iconInfo;
	private final Rect iconDoneRect = new Rect();
	private final Bitmap iconDone;
	private final float dp;

	private int viewWidth;
	private int viewHeight;
	private int radius;
	private int tapTimeout;
	private float touchSlopSq;
	private OpenListListener listListener;
	private AppMenu.Icon iconToEdit;
	private boolean editMode = false;

	public AppPieView(Context context, AttributeSet attr) {
		super(context, attr);

		selectedPaint.setColorFilter(new PorterDuffColorFilter(
				0xffff0000, PorterDuff.Mode.SRC_IN));

		Resources res = context.getResources();
		iconAdd = Converter.getBitmapFromDrawable(
				res.getDrawable(R.drawable.ic_add));
		iconRemove = Converter.getBitmapFromDrawable(
				res.getDrawable(R.drawable.ic_remove));
		iconInfo = Converter.getBitmapFromDrawable(
				res.getDrawable(R.drawable.ic_info));
		iconDone = Converter.getBitmapFromDrawable(
				res.getDrawable(R.drawable.ic_done));
		dp = res.getDisplayMetrics().density;

		float touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
		touchSlopSq = touchSlop * touchSlop;
		tapTimeout = ViewConfiguration.getTapTimeout();

		surfaceHolder = getHolder();
		appMenu.indexAppsAsync(context);

		initSurfaceHolder(surfaceHolder);
		initTouchListener();

		setZOrderOnTop(true);
	}

	public void setOpenListListener(OpenListListener listener) {
		listListener = listener;
	}

	public void addIconInteractive(AppMenu.Icon appIcon, Point from) {
		editIcon(appIcon);
		touch.set(from.x, from.y);
		setCenter(viewWidth >> 1, viewHeight >> 1);
		drawView();
	}

	public boolean isEditMode() {
		return editMode;
	}

	public void endEditMode() {
		appMenu.store(getContext());
		iconsBeforeEdit.clear();
		iconToEdit = null;
		editMode = false;
		invalidateView();
		drawView();
	}

	private void editIcon(AppMenu.Icon icon) {
		appMenu.icons.remove(icon);
		iconsBeforeEdit.clear();
		iconsBeforeEdit.addAll(appMenu.icons);
		iconToEdit = icon;
		editMode = true;
		invalidateView();
	}

	private void initSurfaceHolder(SurfaceHolder holder) {
		holder.setFormat(PixelFormat.TRANSPARENT);
		holder.addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceChanged(
					SurfaceHolder holder,
					int format,
					int width,
					int height) {
				initMenu(width, height);
				if (editMode) {
					invalidateView();
					drawView();
				}
			}

			@Override
			public void surfaceCreated(SurfaceHolder holder) {
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
			}
		});
	}

	private void initMenu(int width, int height) {
		int min = Math.min(width, height);
		float maxIconSize = 64f * dp;
		if (Math.floor(min * .28f) > maxIconSize) {
			min = Math.round(maxIconSize / .28f);
		}
		radius = Math.round(min * .5f);
		viewWidth = width;
		viewHeight = height;
		layoutTouchTargets(height > width);
	}

	private void layoutTouchTargets(boolean portrait) {
		Bitmap[] icons = new Bitmap[]{iconAdd, iconRemove, iconInfo, iconDone};
		Rect[] rects = new Rect[]{iconAddRect, iconRemoveRect, iconInfoRect, iconDoneRect};
		int length = Math.min(icons.length, rects.length);
		int totalWidth = 0;
		int totalHeight = 0;
		int largestWidth = 0;
		int largestHeight = 0;
		// initialize rects and calculate totals
		for (int i = 0; i < length; ++i) {
			Bitmap icon = icons[i];
			int w = icon.getWidth();
			int h = icon.getHeight();
			rects[i].set(0, 0, w, h);
			largestWidth = Math.max(largestWidth, w);
			largestHeight = Math.max(largestHeight, h);
			totalWidth += w;
			totalHeight += h;
		}
		int padding = Math.round(dp * 80f);
		if (portrait) {
			int step = Math.round(
					(float) (viewWidth - totalWidth) / (length + 1));
			int x = step;
			int y = viewHeight - largestHeight - padding;
			for (Rect rect : rects) {
				rect.offset(x, y);
				x += step + rect.width();
			}
		} else {
			int step = Math.round(
					(float) (viewHeight - totalHeight) / (length + 1));
			int x = viewWidth - largestWidth - padding;
			int y = step;
			for (Rect rect : rects) {
				rect.offset(x, y);
				y += step + rect.height();
			}
		}
	}

	private void initTouchListener() {
		setOnTouchListener(new View.OnTouchListener() {
			private Point down = new Point();
			private long downAt;

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				touch.set(Math.round(event.getRawX()),
						Math.round(event.getRawY()));
				switch (event.getActionMasked()) {
					default:
						break;
					case MotionEvent.ACTION_CANCEL:
						invalidateTouch();
						iconToEdit = null;
						drawView();
						break;
					case MotionEvent.ACTION_MOVE:
						drawView();
						break;
					case MotionEvent.ACTION_DOWN:
						if (editMode) {
							editIconAt(touch);
						} else {
							down.set(touch.x, touch.y);
							downAt = event.getEventTime();
							setCenter(touch);
						}
						drawView();
						break;
					case MotionEvent.ACTION_UP:
						v.performClick();
						if (editMode) {
							if (iconAddRect.contains(touch.x, touch.y)) {
								((Activity) getContext()).onBackPressed();
							} else if (iconRemoveRect.contains(
									touch.x, touch.y)) {
								if (iconToEdit != null) {
									appMenu.icons.remove(iconToEdit);
									invalidateView();
								}
							} else if (iconInfoRect.contains(
									touch.x, touch.y)) {
								if (iconToEdit != null) {
									startAppInfo(((AppMenu.AppIcon)
											iconToEdit).packageName);
								}
							} else if (iconDoneRect.contains(
									touch.x, touch.y)) {
								endEditMode();
							}
							iconToEdit = null;
						} else {
							if (SystemClock.uptimeMillis() - downAt <= tapTimeout &&
									distSq(down, touch) <= touchSlopSq) {
								if (listListener != null) {
									listListener.onOpenList();
								}
							} else {
								appMenu.launch(v.getContext());
							}
						}
						invalidateTouch();
						drawView();
						break;
				}
				return true;
			}
		});
	}

	private void startAppInfo(String packageName) {
		Intent intent = new Intent(
				android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.setData(Uri.parse("package:" + packageName));
		((Activity) getContext()).startActivity(intent);
	}

	private void setCenter(Point point) {
		setCenter(point.x, point.y);
	}

	private void setCenter(int x, int y) {
		appMenu.set(
				Math.max(radius, Math.min(viewWidth - radius, x)),
				Math.max(radius, Math.min(viewHeight - radius, y)),
				radius);
	}

	private void editIconAt(Point point) {
		for (int i = 0, size = appMenu.icons.size(); i < size; ++i) {
			AppMenu.Icon icon = appMenu.icons.get(i);
			float sizeSq = Math.round(icon.size * icon.size);
			if (distSq(point.x, point.y, icon.x, icon.y) < sizeSq) {
				editIcon(icon);
				break;
			}
		}
	}

	private void drawView() {
		if (touch.equals(lastTouch)) {
			return;
		}
		Canvas canvas = surfaceHolder.lockCanvas();
		if (canvas == null) {
			return;
		}
		canvas.drawColor(0, PorterDuff.Mode.CLEAR);
		if (shouldShowMenu() || editMode) {
			if (editMode) {
				drawTouchTarget(canvas, iconAdd, iconAddRect);
				drawTouchTarget(canvas, iconRemove, iconRemoveRect);
				drawTouchTarget(canvas, iconInfo, iconInfoRect);
				drawTouchTarget(canvas, iconDone, iconDoneRect);
			}
			if (iconToEdit != null) {
				int size = iconsBeforeEdit.size();
				double step = AppMenu.TAU / (size + 1);
				double angle = AppMenu.getPositiveAngle(Math.atan2(
						touch.y - appMenu.getCenterY(),
						touch.x - appMenu.getCenterX()) + step * .5);
				int insertAt = (int) Math.floor(angle / step);
				appMenu.icons.clear();
				appMenu.icons.addAll(iconsBeforeEdit);
				appMenu.icons.add(Math.min(size, insertAt), iconToEdit);
				appMenu.calculate(touch.x, touch.y);
				iconToEdit.x = touch.x;
				iconToEdit.y = touch.y;
			} else if (editMode) {
				appMenu.calculate(appMenu.getCenterX(), appMenu.getCenterY());
			} else {
				appMenu.calculate(touch.x, touch.y);
			}
			appMenu.draw(canvas);
		}
		lastTouch.set(touch.x, touch.y);
		surfaceHolder.unlockCanvasAndPost(canvas);
	}

	private void invalidateView() {
		lastTouch.set(-2, -2);
	}

	private void invalidateTouch() {
		touch.set(-1, -1);
	}

	private boolean shouldShowMenu() {
		return touch.x > -1;
	}

	private void drawTouchTarget(Canvas canvas, Bitmap icon, Rect rect) {
		canvas.drawBitmap(icon, null, rect, rect.contains(touch.x, touch.y)
				? selectedPaint
				: bitmapPaint);
	}

	private static float distSq(Point a, Point b) {
		return distSq(a.x, a.y, b.x, b.y);
	}

	private static float distSq(int ax, int ay, int bx, int by) {
		float dx = ax - bx;
		float dy = ay - by;
		return dx*dx + dy*dy;
	}
}
