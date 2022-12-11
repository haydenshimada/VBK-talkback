/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;
import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.HIDE_PASSWORD;
import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.NORMAL;
import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.SILENCE;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State.INTRO;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State.NONE;
import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.isAccessibilityServiceEnabled;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.ImeConnection.AnnounceType;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.common.TouchDots;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.common.translate.EditBuffer;
import com.google.android.accessibility.braille.common.translate.EditBufferUtils;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleDots;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay.Result;
import com.google.android.accessibility.braille.interfaces.BrailleImeForTalkBack;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme.ServiceStatus;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import com.google.android.accessibility.brailleime.BrailleImeVibrator.VibrationType;
import com.google.android.accessibility.brailleime.LayoutOrientator.LayoutOrientatorCallback;
import com.google.android.accessibility.brailleime.OrientationMonitor.Orientation;
import com.google.android.accessibility.brailleime.analytics.BrailleImeAnalytics;
import com.google.android.accessibility.brailleime.dialog.ContextMenuDialog;
import com.google.android.accessibility.brailleime.dialog.TalkBackOffDialog;
import com.google.android.accessibility.brailleime.dialog.TalkBackSuspendDialog;
import com.google.android.accessibility.brailleime.dialog.TooFewTouchPointsDialog;
import com.google.android.accessibility.brailleime.dialog.ViewAttachedDialog;
import com.google.android.accessibility.brailleime.input.BrailleDisplayImeStripView;
import com.google.android.accessibility.brailleime.input.BrailleInputView;
import com.google.android.accessibility.brailleime.input.BrailleInputView.FingersPattern;
import com.google.android.accessibility.brailleime.input.Swipe;
import com.google.android.accessibility.brailleime.input.Swipe.Direction;
import com.google.android.accessibility.brailleime.keyboardview.AccessibilityOverlayKeyboardView;
import com.google.android.accessibility.brailleime.keyboardview.KeyboardView;
import com.google.android.accessibility.brailleime.keyboardview.KeyboardView.KeyboardViewCallback;
import com.google.android.accessibility.brailleime.keyboardview.StandardKeyboardView;
import com.google.android.accessibility.brailleime.settings.BrailleImePreferencesActivity;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialCallback;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.keyboard.KeyboardUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.common.annotations.VisibleForTesting;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Optional;

/**
 * An input method intended for blind/low-vision users that displays braille dot touch targets and
 * converts taps on those braille dots into print characters.
 *
 * <p>Since it is the root of the object graph and the class that has access to the {@link
 * InputConnection}, this class coordinates the flow of user input. Chronologically, that input
 * begins with the {@link BrailleInputView}, which is instantiated and owned by this class, and
 * which converts touch events into {@link BrailleInputView.Callback} callbacks, which are
 * implemented inside this class. In the case of {@link
 * BrailleInputView.Callback#onBrailleProduced(BrailleCharacter)} this class passes the {@link
 * BrailleCharacter} object to the {@link EditBuffer}, which holds a list of accumulated {@link
 * BrailleCharacter} until the time comes to translate braille to print and send it to the IME
 * Editor via the {@link InputConnection}.
 *
 * <p>Difficulty arises because the prototypical IME envisioned by the super class has an input area
 * with a candidates bar atop it, and possibly an extracted Editor view at the very top in case the
 * IME needs to be fullscreen. This IME differs from that protoype in two major ways:
 *
 * <ol>
 *   <li>It wants to be immersive - as fullscreen as possible
 *   <li>It has no need to display the underlying Editor nor an extracted Editor
 * </ol>
 *
 * <p>Therefore this class avoids the default View structure of the super class-provided Window in a
 * somewhat complex way, with the strategy depending on the version of the operating system. For
 * more information, see {@link KeyboardView}. One of these strategies involves adding a whole new
 * Window object to the WindowManager, and the other involves defining touch exploration passthrough
 * regions; both of these abilities rely on the BrailleIme being colocated with an
 * AccessibilityService, namely TalkBack. Indeed BrailleIme has a close dependency upon TalkBack,
 * which means that moving BrailleIme out of the TalkBack application would require much work.
 */
public class BrailleIme extends InputMethodService {

  private static final String TAG = "BrailleIme";

  // A note on how the desired hiding of the default IME views is achieved:
  // - Hiding the candidatesArea is simple - simply do not override onCreateCandidatesView.
  // - Hiding the extractArea can be accomplished in either of two ways - either override
  // onEvaluateFullscreenMode() to always return false (which is counterintuitive since this IME is
  // to be fullscreen), or expand the bounds of the inputArea by overriding setInputView(View)
  // and making an ill-advised modification to the LayoutParams of the parent of the
  // BrailleInputView. This code uses the first of these two options; this allows our inputArea,
  // which we furnish in the override of onCreateInputView, to take up the entire view region.

  private static TalkBackForBrailleIme talkBackForBrailleIme;
  private static BrailleDisplayForBrailleIme brailleDisplayForBrailleIme;
  private static final int ANNOUNCE_DELAY_MS =
      800; // Delay, so that it follows previous-IME-is-hidden announcement.
  private static final int ANNOUNCE_CALIBRATION_DELAY_MS = 1500;
  private static final int CALIBRATION_EARCON_DELAY_MS = 500;
  private static final int CALIBRATION_EARCON_REPEAT_COUNT = 3;
  private boolean deviceSupportsAtLeast5Pointers;
  private State tutorialState;
  private EditBuffer editBuffer;
  private Thread.UncaughtExceptionHandler originalDefaultUncaughtExceptionHandler;
  private OrientationMonitor.Callback orientationCallbackDelegate;
  private ViewAttachedDialog talkbackOffDialog;
  private ViewAttachedDialog contextMenuDialog;
  private ViewAttachedDialog tooFewTouchPointsDialog;
  private ViewAttachedDialog talkBackSuspendDialog;
  private LayoutOrientator layoutOrientator;
  private EscapeReminder escapeReminder;
  private BrailleImeAnalytics brailleImeAnalytics;
  private KeyboardView keyboardView;
  private Handler handler;
  private boolean brailleDisplayConnectedAndNotSuspended;

  /** An interface to notify orientation change. */
  public interface OrientationSensitive {
    void onOrientationChanged(int orientation, Size screenSize);
  }

  /** TalkBack invokes this to provide us with the TalkBackForBrailleIme instance. */
  public static void initialize(
      Context context,
      TalkBackForBrailleIme talkBackForBrailleIme,
      BrailleDisplayForBrailleIme brailleDisplayForBrailleIme) {
    BrailleIme.talkBackForBrailleIme = talkBackForBrailleIme;
    BrailleIme.brailleDisplayForBrailleIme = brailleDisplayForBrailleIme;
    Utils.setComponentEnabled(context, Constants.BRAILLE_KEYBOARD, true);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    BrailleImeLog.logD(TAG, "onCreate");

    readDeviceFeatures();
    handler = new Handler();
    if (brailleDisplayForBrailleIme != null) {
      brailleDisplayConnectedAndNotSuspended =
          brailleDisplayForBrailleIme.isBrailleDisplayConnectedAndNotSuspended();
    }
    keyboardView = createKeyboardView();
    escapeReminder = new EscapeReminder(this, escapeReminderCallback);
    talkbackOffDialog = new TalkBackOffDialog(this, talkBackOffDialogCallback);
    contextMenuDialog = new ContextMenuDialog(this, contextMenuDialogCallback);
    tooFewTouchPointsDialog = new TooFewTouchPointsDialog(this, tooFewTouchPointsDialogCallback);
    talkBackSuspendDialog = new TalkBackSuspendDialog(this, talkBackSuspendDialogCallback);
    tutorialState = NONE;
    originalDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(localUncaughtExceptionHandler);

    BrailleUserPreferences.getSharedPreferences(this, BRAILLE_SHARED_PREFS_FILENAME)
        .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
    intentFilter.addAction(Intent.ACTION_SCREEN_ON);
    registerReceiver(screenOffReceiver, intentFilter);
    registerReceiver(
        closeSystemDialogsReceiver, new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    registerReceiver(imeChangeListener, new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED));
    Uri uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    getContentResolver()
        .registerContentObserver(uri, false, accessibilityServiceStatusChangeObserver);

    brailleImeAnalytics = BrailleImeAnalytics.getInstance(this);
    OrientationMonitor.init(this);
    layoutOrientator = new LayoutOrientator(this, layoutOrientatorCallback);

    getWindow().setTitle(Utils.getBrailleKeyboardDisplayName(this));
  }

  @Override
  public void onBindInput() {
    BrailleImeLog.logD(TAG, "onBindInput");
    super.onBindInput();
  }

  @Override
  public View onCreateInputView() {
    View viewForImeFrameworks = keyboardView.createImeInputView();
    if (viewForImeFrameworks.getParent() != null) {
      // Remove any old one, to prevent a leak.
      ((ViewGroup) viewForImeFrameworks.getParent()).removeView(viewForImeFrameworks);
    }
    return viewForImeFrameworks;
  }

  private KeyboardView createKeyboardView() {
    return Utils.useImeSuppliedInputWindow() || brailleDisplayConnectedAndNotSuspended
        ? new StandardKeyboardView(
            this, keyboardViewCallback, /* fullScreen= */ !brailleDisplayConnectedAndNotSuspended)
        : new AccessibilityOverlayKeyboardView(this, keyboardViewCallback);
  }

  @Override
  public boolean onShowInputRequested(int flags, boolean configChange) {
    if (talkBackForBrailleIme != null) {
      if (talkBackForBrailleIme.isContextMenuExist()) {
        BrailleImeLog.logD(TAG, "TalkBack context menu is running.");
        // Reject the request since TalkBack context menu is showing.
        return false;
      }
    }

    return super.onShowInputRequested(flags, configChange);
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting) {
    BrailleImeLog.logD(TAG, "onStartInputView");
    if (Utils.isPhonePermissionGranted(this)) {
      TelephonyManager telephonyManager =
          (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
      telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    boolean brailleDisplayConnectedAndNotIgnored =
        brailleDisplayForBrailleIme != null
            && brailleDisplayForBrailleIme.isBrailleDisplayConnectedAndNotSuspended();
    if (this.brailleDisplayConnectedAndNotSuspended != brailleDisplayConnectedAndNotIgnored) {
      this.brailleDisplayConnectedAndNotSuspended = brailleDisplayConnectedAndNotIgnored;
      updateInputView();
    }

    // Surprisingly, framework sometimes invokes onStartInputView just after the screen turns off;
    // therefore we first confirm that the screen is indeed on before invoking activateIfNeeded.
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    if (pm.isInteractive()) {
      activateIfNeeded();
    } else {
      hideSelf();
    }
    startAnalyticsPossibly();

    InputConnection inputConnection = getCurrentInputConnection();
    if (inputConnection != null) {
      // Invoking requestCursorUpdates causes onUpdateCursorAnchorInfo() to be invoked.
      getCurrentInputConnection()
          .requestCursorUpdates(
              InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR);
    }
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    if (Utils.isPhonePermissionGranted(this)) {
      TelephonyManager telephonyManager =
          (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
      telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }
    // Of the teardown methods, this is the most reliable, so we use it to deactivate.
    BrailleImeLog.logD(TAG, "onFinishInputView");
    super.onFinishInputView(finishingInput);
    deactivateIfNeeded();
    brailleImeAnalytics.collectSessionEvents();
  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    // Why return false here? - see the note atop the class regarding how we suppress Views.
    return false;
  }

  @Override
  public void onDestroy() {
    BrailleImeLog.logD(TAG, "onDestroy");
    BrailleUserPreferences.getSharedPreferences(this, BRAILLE_SHARED_PREFS_FILENAME)
        .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    unregisterReceiver(screenOffReceiver);
    unregisterReceiver(closeSystemDialogsReceiver);
    unregisterReceiver(imeChangeListener);
    getContentResolver().unregisterContentObserver(accessibilityServiceStatusChangeObserver);
    super.onDestroy();
    keyboardView.tearDown();
    keyboardView = null;
    brailleImeAnalytics.sendAllLogs();
  }

  private void activateIfNeeded() {
    BrailleImeLog.logD(TAG, "activateIfNeeded");
    if (keyboardView == null) {
      BrailleImeLog.logE(TAG, "keyboardView is null. Activate should not invoke before onCreate()");
      return;
    }
    if (!isInputViewShown()) {
      // Defer to superclass, if it knows that our input view is not showing (this is not an error).
      return;
    }
    if (keyboardView.isViewContainerCreated()) {
      // Activation is not needed because we're already activated (this is not an error).
      return;
    }
    if (talkBackForBrailleIme == null
        || talkBackForBrailleIme.getServiceStatus() == ServiceStatus.OFF) {
      BrailleImeLog.logE(TAG, "talkBackForBrailleIme is null or Talkback is off.");
      showTalkBackOffDialog();
      return;
    } else if (talkBackForBrailleIme.getServiceStatus() == ServiceStatus.SUSPEND) {
      BrailleImeLog.logE(TAG, "Talkback is suspend.");
      showTalkBackSuspendDialog();
      return;
    }

    if (!deviceSupportsAtLeast5Pointers) {
      showTooFewTouchPointsDialog();
      return;
    }

    BrailleImeLog.logD(TAG, "activate");
    if (talkBackForBrailleIme.isVibrationFeedbackEnabled()) {
      BrailleImeVibrator.getInstance(this).enable();
    }
    createViewContainerAndAddView();
    createEditBuffer();
    OrientationMonitor.getInstance().enable();
    OrientationMonitor.getInstance().registerCallback(orientationMonitorCallback);
  }

  private void createViewContainerAndAddView() {
    keyboardView.setWindowManager(talkBackForBrailleIme.getWindowManager());
    keyboardView.createViewContainer();
    if (brailleDisplayConnectedAndNotSuspended) {
      keyboardView.createAndAddStripView(brailleDisplayKeyboardCallback);
    } else if (tutorialState != NONE
        || BrailleUserPreferences.shouldLaunchTutorial(getApplicationContext())) {
      if (tutorialState == NONE) {
        // Launch tutorial for the first usage.
        tutorialState = INTRO;
      }
      // Restore to previous tutorial state.
      createAndAddTutorialView();
    } else if (!keyboardView.isInputViewCreated()) {
      keyboardView.createAndAddInputView(inputPlaneCallback);
      escapeReminder.startTimer();
    }
  }

  private void createAndAddTutorialView() {
    keyboardView.createAndAddTutorialView(tutorialState, tutorialCallback);
    talkBackForBrailleIme.disableSilenceOnProximity();
  }

  private void activateBrailleIme() {
    if (talkBackForBrailleIme != null && isInputViewShown()) {
      talkBackForBrailleIme.onBrailleImeActivated(
          brailleImeForTalkBack,
          !brailleDisplayConnectedAndNotSuspended,
          Utils.useImeSuppliedInputWindow(),
          // Region might be null for short time before onTalkBackResumed() is called.
          keyboardView.obtainViewContainerRegionOnTheScreen().orElse(null));
    }
  }

  private static void deactivateBrailleIme() {
    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.onBrailleImeInactivated(Utils.useImeSuppliedInputWindow());
    }
  }

  private void showTalkBackOffDialog() {
    // When screen rotates, onStartInputView is called and if there is a dialog showing, keep it
    // showing instead of adding a new one.
    if (!talkbackOffDialog.isShowing()) {
      brailleImeAnalytics.logTalkBackOffDialogDisplay();
      keyboardView.showViewAttachedDialog(talkbackOffDialog);
    }
  }

  private void showTalkBackSuspendDialog() {
    // When screen rotates, onStartInputView is called and if there is a dialog showing, keep it
    // showing instead of adding a new one.
    if (!talkBackSuspendDialog.isShowing()) {
      brailleImeAnalytics.logTalkBackOffDialogDisplay();
      keyboardView.showViewAttachedDialog(talkBackSuspendDialog);
    }
  }

  private void showTooFewTouchPointsDialog() {
    // When screen rotates, onStartInputView is called and if there is a dialog showing, keep it
    // showing instead of adding a new one.
    if (!tooFewTouchPointsDialog.isShowing()) {
      brailleImeAnalytics.logFewTouchPointsDialogDisplay();
      keyboardView.showViewAttachedDialog(tooFewTouchPointsDialog);
    }
  }

  private void createEditBuffer() {
    Code code = BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(this);

    TranslatorFactory translatorFactory = BrailleUserPreferences.readTranslatorFactory();
    editBuffer =
        BrailleLanguages.createEditBuffer(
            this, talkBackForBrailleImeInternal, code, translatorFactory);
  }

  @Override
  public void onComputeInsets(Insets outInsets) {
    if (Utils.useImeSuppliedInputWindow()) {
      // Set the contentTopInsets, which is measured from the top edge of the display, positively
      // downward, to be as tall as possible allowing the underlying framework to provide plenty of
      // vertical space to layout the underlying Activity.  In the absence of setting this value to
      // be large, the underlying Activity, in case it uses windowSoftInputMode adjustResize or
      // adjustUnspecified, will have very little (or zero) vertical room to perform a valid layout
      // - and that causes many problems, such as the IME getting closed or the Editor not receiving
      // our input.
      outInsets.contentTopInsets = Utils.getDisplaySizeInPixels(this).getHeight();
    }

    if (keyboardView.getViewForImeFrameworksSize().isPresent()) {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
        // In Android P, we need to manually set the size of the outInsets which represent the area
        // north of the IME window, otherwise any dialog attached to the unused IME window will not
        // show any foreground contents. But we also need to take care not to set this insets area
        // to be the entire screen, because doing that causes the inputView to be ignored by an
        // accessibility framework class responsible for sending info to Talkback, and this prevents
        // the proper announcement of the IME by TalkBack.
        int visibleTop = keyboardView.getViewForImeFrameworksSize().get().getHeight() - 1;
        outInsets.visibleTopInsets = visibleTop;
        outInsets.contentTopInsets = visibleTop;
        outInsets.touchableRegion.setEmpty();
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_VISIBLE;
      }
    }
  }

  private void deactivateIfNeeded() {
    BrailleImeLog.logD(TAG, "deactivateIfNeeded");
    dismissDialogs();
    escapeReminder.cancelTimer();
    if (!keyboardView.isViewContainerCreated()) {
      // Deactivation is not needed because we're already deactivated (this is not an error).
      return;
    }
    if (talkBackForBrailleIme == null) {
      BrailleImeLog.logE(TAG, "talkBackForBrailleIme is null");
      return;
    }
    BrailleImeLog.logD(TAG, "deactivate");
    BrailleImeVibrator.getInstance(this).disable();
    if (isConnectionValid()) {
      editBuffer.commit(getImeConnection());
    }

    deactivateBrailleIme();
    tutorialState = keyboardView.getTutorialStatus();
    keyboardView.tearDown();
    OrientationMonitor.getInstance().unregisterCallback();
    OrientationMonitor.getInstance().disable();
  }

  private void reactivate() {
    deactivateIfNeeded();
    activateIfNeeded();
  }

  private void hideSelf() {
    requestHideSelf(0);
  }

  /**
   * Performs the 'actions' specified, via imeOptions, by the application such as 'Send'. A typical
   * IME usually surfaces the trigger for such an action with an (often blue-colored) action button.
   *
   * <p>By far the most common case is for an application to specify a single action (as opposed to
   * multiple). We don't currently support the distinguishment of multiple actions in that case.
   *
   * <p>Return {@code true} if the keyboard should remain showing.
   */
  private void performEnterAction(InputConnection inputConnection) {
    EditorInfo editorInfo = getCurrentInputEditorInfo();
    int editorAction = editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
    BrailleImeLog.logD(TAG, "performEnterAction editorAction = " + editorAction);
    if (editorAction != EditorInfo.IME_ACTION_UNSPECIFIED
        && editorAction != EditorInfo.IME_ACTION_NONE) {
      if (Constants.ANDROID_MESSAGES_PACKAGE_NAME.equals(editorInfo.packageName)) {
        // Messages uses async thread to check conditions when performing submit. We pend the task
        // with 50 millis seconds to avoid perform action failed.
        new Handler().postDelayed(() -> inputConnection.performEditorAction(editorAction), 50);
      } else {
        inputConnection.performEditorAction(editorAction);
      }
      if (editorAction == EditorInfo.IME_ACTION_NEXT) {
        talkBackForBrailleImeInternal.speakEnqueue(getString(R.string.perform_action_next));
      } else {
        talkBackForBrailleImeInternal.speakEnqueue(getString(R.string.perform_action_submitting));
      }
    }
  }

  private void updateInputView() {
    handler.post(
        () -> {
          if (keyboardView != null) {
            keyboardView.tearDown();
          }
          keyboardView = createKeyboardView();
          setInputView(keyboardView.createImeInputView());
          createViewContainerAndAddView();
        });
  }

  /**
   * Attempt to exit this IME and switch to another.
   *
   * <p>First, try switching to Gboard if it exists. Otherwise, switch to the next IME if one
   * exists.
   *
   * <p>If switching to the next IME fails (which can happen because there are no other IMEs
   * installed and enabled OR for an unknown reason (which DOES occur on some phones), show the
   * system IME picker if there is another IME installed and enabled.
   *
   * <p>Finally, if there are not other IMEs installed and enabled, launch IME Settings.
   */
  @VisibleForTesting
  boolean switchToNextInputMethod() {
    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.interruptSpeak();
    }
    if (!KeyboardUtils.areMultipleImesEnabled(this)) {
      // Show a toast and bring up Ime settings to user.
      Toast.makeText(this, getString(R.string.bring_ime_settings_page), Toast.LENGTH_SHORT).show();
      Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
      intent.addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_CLEAR_TASK
              | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      startActivity(intent);
      return false;
    }
    boolean succeeded;
    // Default switch to gboard.
    String inputMethodInfoId = KeyboardUtils.getEnabledImeId(this, Constants.GBOARD_PACKAGE_NAME);
    if (!TextUtils.isEmpty(inputMethodInfoId)) {
      // This api doesn't tell us switch succeed or not. Assume it switch successfully.
      switchInputMethod(inputMethodInfoId);
      succeeded = true;
    } else if (BuildVersionUtils.isAtLeastP()) {
      succeeded = switchToNextInputMethod(false);
    } else {
      IBinder token = getWindow().getWindow().getAttributes().token;
      InputMethodManager inputMethodManager =
          (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      succeeded = inputMethodManager.switchToNextInputMethod(token, false);
    }
    // REFERTO: Switch to next keyboard manually by giving ime id.
    if (!succeeded) {
      // This api doesn't tell us switch succeed or not. Assume it switch successfully.
      switchInputMethod(KeyboardUtils.getNextEnabledImeId(this));
    }
    return true;
  }

  private void readDeviceFeatures() {
    PackageManager pm = getPackageManager();
    deviceSupportsAtLeast5Pointers =
        pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND);
  }

  private void dismissDialogs() {
    talkbackOffDialog.dismiss();
    contextMenuDialog.dismiss();
    tooFewTouchPointsDialog.dismiss();
    talkBackSuspendDialog.dismiss();
  }

  // Starts to log keyboard session only in non tutorial mode.
  private void startAnalyticsPossibly() {
    if (tutorialState == NONE) {
      brailleImeAnalytics.startSession();
    }
  }

  private final LayoutOrientatorCallback layoutOrientatorCallback =
      new LayoutOrientatorCallback() {
        @Override
        public boolean useSensorsToDetectLayout() {
          return BrailleUserPreferences.readLayoutMode(BrailleIme.this) == TouchDots.AUTO_DETECT
              && !keyboardView.isTutorialShown()
              && !brailleDisplayConnectedAndNotSuspended;
        }

        @Override
        public void onDetectionChanged(boolean isTabletop, boolean isFirstChangedEvent) {
          String readoutString =
              getString(
                  isTabletop
                      ? R.string.switch_to_tabletop_announcement
                      : R.string.switch_to_screen_away_announcement);
          if (isFirstChangedEvent) {
            talkBackForBrailleImeInternal.speakEnqueue(readoutString, ANNOUNCE_DELAY_MS);
          } else {
            talkBackForBrailleImeInternal.speakInterrupt(readoutString);
          }
          keyboardView.setTableMode(isTabletop);
        }
      };

  private final Thread.UncaughtExceptionHandler localUncaughtExceptionHandler =
      new UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
          BrailleImeLog.logE(TAG, "Uncaught exception", throwable);
          try {
            deactivateIfNeeded();
            if (isInputViewShown()) {
              switchToNextInputMethod();
            }
          } catch (Exception e) {
            BrailleImeLog.logE(TAG, "Uncaught exception in handler", throwable);
          } finally {
            if (originalDefaultUncaughtExceptionHandler != null) {
              originalDefaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
            }
          }
        }
      };

  private final BroadcastReceiver screenOffReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            BrailleImeLog.logD(TAG, "screen off");
            deactivateIfNeeded();
            dismissDialogs();
            // Finish session while screen off because no called onFinishInputView() in this case.
            brailleImeAnalytics.collectSessionEvents();
          } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            // Activate upon SCREEN_ON to resolve the following scenario occurs:
            // 1. Screen turns off, and then abruptly turns on (before SCREEN_OFF receiver is
            // triggered).
            // 2. onStartInputView() gets invoked before SCREEN_OFF receiver gets triggered.
            // 3. SCREEN_OFF receiver gets triggered, thus deactivating, causing bad state - IME is
            // up but Window is absent.
            BrailleImeLog.logD(TAG, "screen on");
            KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            BrailleImeLog.logD(TAG, "screen is locked: " + keyguardManager.isKeyguardLocked());
            // Do not activate if keyguard is showing (because our Window would show atop keyguard).
            if (!keyguardManager.isKeyguardLocked()) {
              activateIfNeeded();
            }
          }
        }
      };

  private final BroadcastReceiver closeSystemDialogsReceiver =
      new BroadcastReceiver() {
        private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
        private static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
        private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
        private static final String SYSTEM_DIALOG_REASON_VOICE_INTERACTION = "voiceinteraction";

        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
            String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
            if (reason != null) {
              BrailleImeLog.logD(TAG, "action:" + intent.getAction() + ",reason:" + reason);
              if (reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)
                  || reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)
                  || reason.equals(SYSTEM_DIALOG_REASON_VOICE_INTERACTION)) {
                // Home key, recent key or google assistant comes up.
                dismissDialogs();
              }
            }
          }
        }
      };

  private final BroadcastReceiver imeChangeListener =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_INPUT_METHOD_CHANGED)) {
            dismissDialogs();
          }
        }
      };

  private final BrailleImeForTalkBack brailleImeForTalkBack =
      new BrailleImeForTalkBack() {
        @Override
        public void onTalkBackSuspended() {
          BrailleImeLog.logD(TAG, "onTalkBackSuspended");
          // We might get service state off when TalkBack turns off, but we'll handle it in
          // accessibilityServiceStatusChangeObserver.
          if (isInputViewShown()
              && talkBackForBrailleIme.getServiceStatus() == ServiceStatus.SUSPEND) {
            if (keyboardView.isTutorialShown()) {
              brailleImeAnalytics.logTutorialFinishedByTalkbackStop();
            }
            if (KeyboardUtils.areMultipleImesEnabled(BrailleIme.this)) {
              switchToNextInputMethod();
            } else {
              deactivateIfNeeded();
              showTalkBackSuspendDialog();
            }
          }
        }

        @Override
        public void onTalkBackResumed() {
          BrailleImeLog.logD(TAG, "onTalkBackResumed");
          // This callback won't be triggered when service state changes from off to on because it's
          // set to null when off so we register it back in
          // accessibilityServiceStatusChangeObserver.
          if (isInputViewShown()) {
            dismissDialogs();
            activateIfNeeded();
          }
        }

        @Override
        public BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay() {
          return brailleImeForBrailleDisplay;
        }
      };

  // We need this because in some situations BrailleImeForTalkBack is set to null. There is no
  // callback (set when onBrailleImeActivated()) in TB for us to know it turns off or on. For
  // example, when TalkBackOff dialog shows up, first page of tutorial or context menu shows up.
  // Note: TalkBack turns from active to suspended and suspended to resumed will not come in through
  // this callback.
  private final ContentObserver accessibilityServiceStatusChangeObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          if (!isInputViewShown()) {
            return;
          }
          if (isAccessibilityServiceEnabled(
              BrailleIme.this, Constants.TALKBACK_SERVICE.flattenToShortString())) {
            BrailleImeLog.logD(TAG, "TalkBack becomes active.");
            // This listener is triggered before TB service is ready. Call activateIfNeeded() will
            // get service state is off so we need to set BrailleImeForTalkBack in TB to get
            // onTalkBackResumed() to make sure the state has been set to active.
            activateBrailleIme();
          } else {
            BrailleImeLog.logD(TAG, "TalkBack becomes inactive.");
            if (KeyboardUtils.areMultipleImesEnabled(BrailleIme.this)) {
              switchToNextInputMethod();
            } else {
              deactivateIfNeeded();
              showTalkBackOffDialog();
            }
          }
        }
      };

  private final OrientationMonitor.Callback orientationMonitorCallback =
      new OrientationMonitor.Callback() {
        @Override
        public void onOrientationChanged(OrientationMonitor.Orientation orientation) {
          if (orientationCallbackDelegate != null) {
            orientationCallbackDelegate.onOrientationChanged(orientation);
          }
        }
      };

  private final TalkBackSpeaker talkBackForBrailleImeInternal =
      new TalkBackSpeaker() {
        @Override
        public void speak(
            CharSequence text,
            int delayMs,
            int queueMode,
            UtteranceCompleteRunnable utteranceCompleteRunnable) {
          if (BrailleIme.talkBackForBrailleIme != null) {
            SpeakOptions speakOptions =
                SpeakOptions.create()
                    .setQueueMode(queueMode)
                    .setFlags(
                        FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                            | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE)
                    .setCompletedAction(utteranceCompleteRunnable);
            talkBackForBrailleIme.speak(text, delayMs, speakOptions);
          }
        }
      };

  private final BrailleInputView.Callback inputPlaneCallback =
      new BrailleInputView.Callback() {
        @Override
        public void onSwipeProduced(Swipe swipe) {
          int touchCount = swipe.getTouchCount();
          Direction direction = swipe.getDirection();
          boolean valid = true;
          if (direction == Direction.DOWN && touchCount == 2) {
            BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.OTHER_GESTURES);
            hideSelf();
            brailleImeAnalytics.logGestureActionCloseKeyboard();
            brailleImeAnalytics.sendAllLogs();
            escapeReminder.increaseExitKeyboardCounter();
          } else if (direction == Direction.DOWN && touchCount == 3) {
            BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.OTHER_GESTURES);
            switchToNextInputMethod();
            brailleImeAnalytics.logGestureActionSwitchKeyboard();
            escapeReminder.increaseExitKeyboardCounter();
          } else if (direction == Direction.UP && touchCount == 3) {
            BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.OTHER_GESTURES);
            showContextMenu();
          } else if (direction == Direction.RIGHT && touchCount == 3) {
            // Braille keyboard view is forced to be in landscape. When device is portrait and user
            // swipes upward in screen away mode, for keyboard view, it's swipe rightward.
            if (!isCurrentTableTopMode()
                && OrientationMonitor.getInstance().getCurrentOrientation()
                    == Orientation.PORTRAIT) {
              BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.OTHER_GESTURES);
              showContextMenu();
            }
          } else if (direction == Direction.LEFT && touchCount == 3) {
            // Braille keyboard view is forced to be in landscape. When device is portrait and user
            // swipes upward in tabletop mode, for keyboard view, it's swipe leftward.
            if (isCurrentTableTopMode()
                && OrientationMonitor.getInstance().getCurrentOrientation()
                    == Orientation.PORTRAIT) {
              BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.OTHER_GESTURES);
              showContextMenu();
            }
          } else {
            if (!isConnectionValid()) {
              return;
            }
            ImeConnection imeConnection = getImeConnection();

            if (direction == Direction.UP && touchCount == 2) {
              editBuffer.commit(imeConnection);
              hideSelf(); // Restore EBT so a11y focus could jump to next field.
              performEnterAction(getCurrentInputConnection());
              BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.OTHER_GESTURES);
              brailleImeAnalytics.logGestureActionSubmitText();
              brailleImeAnalytics.collectSessionEvents();
            } else if (direction == Direction.LEFT && touchCount == 1) {
              editBuffer.appendSpace(imeConnection);
              BrailleImeVibrator.getInstance(BrailleIme.this)
                  .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR);
              brailleImeAnalytics.logGestureActionKeySpace();
            } else if (direction == Direction.LEFT && touchCount == 2) {
              editBuffer.appendNewline(imeConnection);
              BrailleImeVibrator.getInstance(BrailleIme.this)
                  .vibrate(VibrationType.NEWLINE_OR_DELETE_WORD);
              brailleImeAnalytics.logGestureActionKeyNewline();
            } else if (direction == Direction.RIGHT && touchCount == 1) {
              editBuffer.deleteCharacterBackward(imeConnection);
              BrailleImeVibrator.getInstance(BrailleIme.this)
                  .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR);
              brailleImeAnalytics.logGestureActionKeyDeleteCharacter();
            } else if (direction == Direction.RIGHT && touchCount == 2) {
              editBuffer.deleteWord(imeConnection);
              BrailleImeVibrator.getInstance(BrailleIme.this)
                  .vibrate(VibrationType.NEWLINE_OR_DELETE_WORD);
              brailleImeAnalytics.logGestureActionKeyDeleteWord();
            } else if (direction == Direction.UP && touchCount == 1) {
              if (talkBackForBrailleIme.shouldUseCharacterGranularity()) {
                editBuffer.moveCursorBackward(imeConnection);
              } else {
                editBuffer.commit(imeConnection);
                talkBackForBrailleIme.moveCursorBackward();
              }
              BrailleImeVibrator.getInstance(BrailleIme.this)
                  .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR);
            } else if (direction == Direction.DOWN && touchCount == 1) {
              if (talkBackForBrailleIme.shouldUseCharacterGranularity()) {
                editBuffer.moveCursorForward(imeConnection);
              } else {
                editBuffer.commit(imeConnection);
                talkBackForBrailleIme.moveCursorForward();
              }
              BrailleImeVibrator.getInstance(BrailleIme.this)
                  .vibrate(VibrationType.SPACE_DELETE_OR_MOVE_CURSOR);
            } else {
              valid = false;
              BrailleImeLog.logD(TAG, "unknown swipe");
            }
          }
          if (valid && !brailleDisplayConnectedAndNotSuspended) {
            escapeReminder.restartTimer();
          }
        }

        @Override
        public boolean isHoldRecognized(int pointersHeldCount) {
          // For calibration.
          return pointersHeldCount >= 5 || pointersHeldCount == 3;
        }

        @Override
        public void onHoldProduced(int pointersHeldCount) {
          // Do nothing.
        }

        @Nullable
        @Override
        public String onBrailleProduced(BrailleCharacter brailleChar) {
          if (!isConnectionValid()) {
            return null;
          }
          brailleImeAnalytics.logTotalBrailleCharCount(1);
          String result = editBuffer.appendBraille(getImeConnection(), brailleChar);
          if (!TextUtils.isEmpty(result)) {
            escapeReminder.restartTimer();
          }
          BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.BRAILLE_COMMISSION);
          return result;
        }

        @Override
        public void onCalibration(FingersPattern fingersPattern) {
          boolean reverseDot = BrailleUserPreferences.readReverseDotsMode(BrailleIme.this);
          if (fingersPattern.equals(FingersPattern.SIX_FINGERS)) {
            String announcement = getString(R.string.calibration_finish_announcement);
            playCalibrationDoneSoundAndAnnouncement(announcement);
            keyboardView.saveInputViewPoints();
          } else if (fingersPattern.equals(FingersPattern.REMAINING_THREE_FINGERS)) {
            String announcement = getString(R.string.remaining_calibration_finish_announcement);
            playCalibrationDoneSoundAndAnnouncement(announcement);
            keyboardView.saveInputViewPoints();
          } else if (fingersPattern.equals(FingersPattern.FIVE_FINGERS)) {
            talkBackForBrailleImeInternal.speakEnqueue(
                getString(
                    reverseDot
                        ? R.string.calibration_step1_hold_right_finger_announcement
                        : R.string.calibration_step1_hold_left_finger_announcement));
          } else if (fingersPattern.equals(FingersPattern.FIRST_THREE_FINGERS)) {
            String announcement =
                getString(
                    reverseDot
                        ? R.string.calibration_step2_hold_left_finger_announcement
                        : R.string.calibration_step2_hold_right_finger_announcement);
            playCalibrationDoneSoundAndAnnouncement(announcement);
          } else if (fingersPattern.equals(FingersPattern.UNKNOWN)) {
            talkBackForBrailleImeInternal.speakEnqueue(
                getString(R.string.calibration_fail_announcement));
          }
        }

        private void playCalibrationDoneSoundAndAnnouncement(String announcement) {
          for (int i = 0; i < CALIBRATION_EARCON_REPEAT_COUNT; i++) {
            talkBackForBrailleIme.playSound(
                R.raw.calibration_done, CALIBRATION_EARCON_DELAY_MS * i);
          }
          // Wait a second for playing sound and then speak the post-action announcement.
          talkBackForBrailleImeInternal.speakEnqueue(announcement, ANNOUNCE_CALIBRATION_DELAY_MS);
        }

        private void showContextMenu() {
          keyboardView.showViewAttachedDialog(contextMenuDialog);
          brailleImeAnalytics.logGestureActionOpenOptionsMenu();
          brailleImeAnalytics.collectSessionEvents();
          escapeReminder.increaseOptionDialogCounter();
        }
      };

  private final KeyboardViewCallback keyboardViewCallback =
      new KeyboardViewCallback() {
        @Override
        public void onViewAdded() {
          activateBrailleIme();
          layoutOrientator.startIfNeeded();
        }

        @Override
        public void onViewUpdated() {
          if (!contextMenuDialog.isShowing() && tutorialState != INTRO) {
            activateBrailleIme();
          }
        }

        @Override
        public void onViewCleared() {
          layoutOrientator.stop();
        }

        @Override
        public void onAnnounce(String announcement, int delayMs) {
          if (delayMs <= 0) {
            talkBackForBrailleImeInternal.speakEnqueue(announcement);
          } else {
            talkBackForBrailleImeInternal.speakEnqueue(announcement, delayMs);
          }
        }
      };

  private final BrailleDisplayImeStripView.CallBack brailleDisplayKeyboardCallback =
      new BrailleDisplayImeStripView.CallBack() {
        @Override
        public void onClicked() {
          BrailleImeLog.logD(TAG, "onStripClicked");
          brailleDisplayConnectedAndNotSuspended = false;
          updateInputView();
          keyboardView.setTableMode(isCurrentTableTopMode());
          activateBrailleIme();
          brailleDisplayForBrailleIme.suspendInFavorOfBrailleKeyboard();
        }
      };

  private void showOnBrailleDisplay() {
    if (brailleDisplayForBrailleIme == null || editBuffer == null) {
      return;
    }
    handler.post(
        () -> {
          ResultForDisplay result =
              ResultForDisplay.builder()
                  .setHoldingsInfo(editBuffer.getHoldingsInfo(getImeConnection()))
                  .setOnScreenText(EditBufferUtils.getTextFieldText(getCurrentInputConnection()))
                  .setTextSelectionRange(
                      BrailleCommonUtils.getTextSelectionRange(getCurrentInputConnection()))
                  .setIsMultiLine(
                      EditBufferUtils.isMultiLineField(getCurrentInputEditorInfo().inputType))
                  .setAction(Utils.getActionLabel(this, getCurrentInputEditorInfo()).toString())
                  .setHint(Utils.getHint(getCurrentInputEditorInfo()).toString())
                  .build();
          brailleDisplayForBrailleIme.showOnDisplay(result);
        });
  }

  private boolean isConnectionValid() {
    if (getCurrentInputConnection() == null) {
      BrailleImeLog.logE(TAG, "lack of InputConnection");
      return false;
    }
    if (getCurrentInputEditorInfo() == null) {
      BrailleImeLog.logE(TAG, "lack of InputEditorInfo");
      return false;
    }
    return true;
  }

  @VisibleForTesting
  boolean isCurrentTableTopMode() {
    Optional<TouchDots> layoutOptional = layoutOrientator.getDetectedLayout();
    TouchDots mode = BrailleUserPreferences.readLayoutMode(this);
    return mode == TouchDots.TABLETOP
        || (mode == TouchDots.AUTO_DETECT
            && layoutOptional.isPresent()
            && layoutOptional.get() == TouchDots.TABLETOP);
  }

  private ImeConnection getImeConnection() {
    AnnounceType announceType = SILENCE;
    if (talkBackForBrailleIme.shouldAnnounceCharacter()) {
      announceType = talkBackForBrailleIme.shouldSpeakPassword() ? NORMAL : HIDE_PASSWORD;
    }
    return new ImeConnection(
        getCurrentInputConnection(), getCurrentInputEditorInfo(), announceType);
  }

  private Result mapToResult(boolean returnValue) {
    if (returnValue) {
      return Result.SUCCESS;
    }
    if (EditBufferUtils.isCursorAtEdge(getCurrentInputConnection())) {
      return Result.REACH_EDGE;
    }
    return Result.INVALID_INPUT_CONNECTION;
  }

  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (key.equals(getString(R.string.pref_brailleime_translator_code))) {
            Code newCode =
                BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(BrailleIme.this);
            talkBackForBrailleImeInternal.speakInterrupt(
                getString(
                    R.string.switch_to_language_announcement,
                    newCode.getUserFacingName(getResources())));
            if (editBuffer != null) {
              editBuffer.commit(getImeConnection());
            }
            createEditBuffer();
          }
        }
      };

  private final ContextMenuDialog.Callback contextMenuDialogCallback =
      new ContextMenuDialog.Callback() {
        @Override
        public void onDialogHidden() {
          activateBrailleIme();
          startAnalyticsPossibly();
          layoutOrientator.startIfNeeded();
        }

        @Override
        public void onDialogShown() {
          deactivateBrailleIme();
          layoutOrientator.stop();
        }

        @Override
        public void onLaunchSettings() {
          Intent intent = new Intent();
          ComponentName name =
              new ComponentName(getPackageName(), BrailleImePreferencesActivity.class.getName());
          intent.setComponent(name);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
        }

        @Override
        public void onTutorialOpen() {
          escapeReminder.cancelTimer();
          layoutOrientator.stop();
          tutorialState = INTRO;
          createAndAddTutorialView();
        }

        @Override
        public void onTutorialClosed() {
          escapeReminder.startTimer();
        }
      };

  private final TutorialCallback tutorialCallback =
      new TutorialCallback() {
        @Override
        public void onBrailleImeActivated() {
          activateBrailleIme();
        }

        @Override
        public void onBrailleImeInactivated() {
          deactivateBrailleIme();
        }

        @Override
        public void onAudialAnnounce(
            String announcement, int delayMs, UtteranceCompleteRunnable utteranceCompleteRunnable) {
          talkBackForBrailleImeInternal.speakInterrupt(
              announcement, delayMs, utteranceCompleteRunnable);
        }

        @Override
        public void onPlaySound(int resId, int delayMs) {
          talkBackForBrailleIme.playSound(resId, delayMs);
        }

        @Override
        public void onSwitchToNextInputMethod() {
          switchToNextInputMethod();
          brailleImeAnalytics.logTutorialFinishedBySwitchToNextInputMethod();
        }

        @Override
        public void onLaunchSettings() {
          contextMenuDialogCallback.onLaunchSettings();
          brailleImeAnalytics.logTutorialFinishedByLaunchSettings();
        }

        @Override
        public void onTutorialFinished() {
          BrailleUserPreferences.setTutorialFinished(getApplicationContext());
          tutorialState = NONE;
          talkBackForBrailleIme.restoreSilenceOnProximity();
          activateBrailleIme();
          keyboardView.createAndAddInputView(inputPlaneCallback);
          // Braille keyboard sometimes will restart. startTimer() might be called twice (here and
          // activateIfNeeded).
          escapeReminder.startTimer();
          brailleImeAnalytics.logTutorialFinishedByTutorialCompleted();
          layoutOrientator.startIfNeeded();
        }

        @Override
        public void onRestartTutorial() {
          reactivate();
        }

        @Override
        public void registerOrientationChange(OrientationMonitor.Callback callBack) {
          orientationCallbackDelegate = callBack;
        }

        @Override
        public void unregisterOrientationChange() {
          orientationCallbackDelegate = null;
        }
      };

  private final EscapeReminder.Callback escapeReminderCallback =
      new EscapeReminder.Callback() {
        @Override
        public void onRemind(SpeechController.UtteranceCompleteRunnable utteranceCompleteRunnable) {
          talkBackForBrailleImeInternal.speakEnqueue(
              getString(R.string.reminder_announcement),
              ANNOUNCE_DELAY_MS,
              utteranceCompleteRunnable);
        }

        @Override
        public boolean shouldAnnounce() {
          return keyboardView.isViewContainerCreated() && tutorialState == NONE;
        }
      };

  private final TalkBackOffDialog.Callback talkBackOffDialogCallback =
      new TalkBackOffDialog.Callback() {
        @Override
        public void onSwitchToNextIme() {
          switchToNextInputMethod();
        }

        @Override
        public void onLaunchSettings() {
          Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
          intent.addFlags(
              Intent.FLAG_ACTIVITY_NEW_TASK
                  | Intent.FLAG_ACTIVITY_CLEAR_TASK
                  | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
          // Highlight TalkBack item in Accessibility Settings upon arriving there (Pixel only).
          Utils.attachSettingsHighlightBundle(intent, Constants.TALKBACK_SERVICE);
          startActivity(intent);
          // The ACTION_CLOSE_SYSTEM_DIALOGS intent action is deprecated from S. The platform will
          // automatically collapse the proper system dialogs in the proper use-cases.
          if (!BuildVersionUtils.isAtLeastS()) {
            // Collapse notification panel (quick settings).
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
          }
        }
      };

  private final TalkBackSuspendDialog.Callback talkBackSuspendDialogCallback =
      new TalkBackSuspendDialog.Callback() {
        @Override
        public void onSwitchToNextIme() {
          switchToNextInputMethod();
        }
      };

  private final TooFewTouchPointsDialog.Callback tooFewTouchPointsDialogCallback =
      new TooFewTouchPointsDialog.Callback() {
        @Override
        public void onSwitchToNextIme() {
          switchToNextInputMethod();
        }
      };

  private final PhoneStateListener phoneStateListener =
      new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
          if (state == TelephonyManager.CALL_STATE_RINGING) {
            // Close keyboard when phone call coming.
            if (keyboardView.isViewContainerCreated()) {
              hideSelf();
            }
          }
        }
      };

  private final BrailleImeForBrailleDisplay brailleImeForBrailleDisplay =
      new BrailleImeForBrailleDisplay() {
        @Override
        public void onBrailleDisplayConnected() {
          BrailleImeLog.logD(TAG, "onBrailleDisplayConnected");
          brailleDisplayConnectedAndNotSuspended = true;
          updateInputView();
          activateBrailleIme();
        }

        @Override
        public void onBrailleDisplayDisconnected() {
          BrailleImeLog.logD(TAG, "onBrailleDisplayDisconnected");
          brailleDisplayConnectedAndNotSuspended = false;
          updateInputView();
          activateBrailleIme();
        }

        @Override
        public boolean sendBrailleDots(BrailleCharacter brailleCharacter) {
          if (BrailleUserPreferences.readReverseDotsMode(getApplicationContext())) {
            brailleCharacter = brailleCharacter.toMirror();
          }
          keyboardView.getStripView().animateInput(brailleCharacter.toDotNumbers());
          boolean result;
          if (brailleCharacter.toInt() == BrailleDots.EMPTY_CELL) {
            editBuffer.appendSpace(getImeConnection());
            result = true;
          } else if (brailleCharacter.toInt() == BrailleDots.DOT7) {
            result = deleteBackward();
          } else if (brailleCharacter.toInt() == BrailleDots.DOT8) {
            editBuffer.commit(getImeConnection());
            result = performEnterKeyAction();
          } else {
            editBuffer.appendBraille(getImeConnection(), brailleCharacter);
            result = true;
          }
          showOnBrailleDisplay();
          return result;
        }

        @Override
        public Result moveCursorForward() {
          boolean result = editBuffer.moveCursorForward(getImeConnection());
          if (result) {
            showOnBrailleDisplay();
          }
          return mapToResult(result);
        }

        @Override
        public Result moveCursorBackward() {
          boolean result = editBuffer.moveCursorBackward(getImeConnection());
          if (result) {
            showOnBrailleDisplay();
          }
          return mapToResult(result);
        }

        @Override
        public boolean moveCursorForwardByLine() {
          if (!EditBufferUtils.isMultiLineField(getImeConnection().editorInfo.inputType)) {
            return false;
          }
          boolean result = editBuffer.moveCursorForwardByLine(getImeConnection());
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveCursorBackwardByLine() {
          if (!EditBufferUtils.isMultiLineField(getImeConnection().editorInfo.inputType)) {
            return false;
          }
          boolean result = editBuffer.moveCursorBackwardByLine(getImeConnection());
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveTextFieldCursor(int toIndex) {
          boolean result = editBuffer.moveTextFieldCursor(getImeConnection(), toIndex);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveHoldingsCursor(int toIndex) {
          boolean result = editBuffer.moveHoldingsCursor(getImeConnection(), toIndex);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean deleteBackward() {
          editBuffer.deleteCharacterBackward(getImeConnection());
          showOnBrailleDisplay();
          return true;
        }

        @Override
        public boolean deleteForward() {
          editBuffer.deleteCharacterForward(getImeConnection());
          showOnBrailleDisplay();
          return true;
        }

        @Override
        public boolean submit() {
          editBuffer.commit(getImeConnection());
          showOnBrailleDisplay();
          performEnterAction(getCurrentInputConnection());
          return true;
        }

        @Override
        public boolean performEnterKeyAction() {
          if (getCurrentInputConnection()
              .sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))) {
            return getCurrentInputConnection()
                .sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
          }
          return false;
        }

        @Override
        public void hideKeyboard() {
          hideSelf();
        }

        @Override
        public void updateResultForDisplay() {
          showOnBrailleDisplay();
        }
      };

  @VisibleForTesting
  public ContextMenuDialog.Callback testing_getContextMenuDialogCallback() {
    return contextMenuDialogCallback;
  }

  @VisibleForTesting
  public BrailleImeForTalkBack testing_getBrailleImeForTalkBack() {
    return brailleImeForTalkBack;
  }

  @VisibleForTesting
  public BrailleInputView.Callback testing_getInputPlaneCallback() {
    return inputPlaneCallback;
  }

  @VisibleForTesting
  public void testing_setEditBuffer(EditBuffer editBuffer) {
    this.editBuffer = editBuffer;
  }

  @VisibleForTesting
  public KeyboardView testing_getKeyboardView() {
    return keyboardView;
  }

  @VisibleForTesting
  public void testing_setBrailleImeAnalytics(BrailleImeAnalytics brailleImeAnalytics) {
    this.brailleImeAnalytics = brailleImeAnalytics;
  }

  @VisibleForTesting
  public void testing_setTalkBackOffDialog(TalkBackOffDialog dialog) {
    talkbackOffDialog = dialog;
  }

  @VisibleForTesting
  public BrailleDisplayImeStripView.CallBack testing_getStripViewCallback() {
    return brailleDisplayKeyboardCallback;
  }
}
