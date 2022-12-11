/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback;

import static android.content.Intent.ACTION_ASSIST;
import static android.content.Intent.ACTION_VOICE_COMMAND;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.google.android.accessibility.talkback.Feedback.AdjustValue.Action.DECREASE_VALUE;
import static com.google.android.accessibility.talkback.Feedback.AdjustVolume.Action.DECREASE_VOLUME;
import static com.google.android.accessibility.talkback.Feedback.SpeechRate.Action.INCREASE_RATE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.widget.Toast;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback.AdjustValue;
import com.google.android.accessibility.talkback.Feedback.AdjustVolume;
import com.google.android.accessibility.talkback.Feedback.ContinuousRead;
import com.google.android.accessibility.talkback.Feedback.DeviceInfo;
import com.google.android.accessibility.talkback.Feedback.DimScreen;
import com.google.android.accessibility.talkback.Feedback.EditText;
import com.google.android.accessibility.talkback.Feedback.Focus;
import com.google.android.accessibility.talkback.Feedback.FocusDirection;
import com.google.android.accessibility.talkback.Feedback.Gesture;
import com.google.android.accessibility.talkback.Feedback.ImageCaption;
import com.google.android.accessibility.talkback.Feedback.Label;
import com.google.android.accessibility.talkback.Feedback.Language;
import com.google.android.accessibility.talkback.Feedback.NodeAction;
import com.google.android.accessibility.talkback.Feedback.PassThroughMode;
import com.google.android.accessibility.talkback.Feedback.Scroll;
import com.google.android.accessibility.talkback.Feedback.ShowToast;
import com.google.android.accessibility.talkback.Feedback.Sound;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Feedback.SpeechRate;
import com.google.android.accessibility.talkback.Feedback.SystemAction;
import com.google.android.accessibility.talkback.Feedback.TalkBackUI;
import com.google.android.accessibility.talkback.Feedback.TriggerIntent;
import com.google.android.accessibility.talkback.Feedback.UiChange;
import com.google.android.accessibility.talkback.Feedback.Vibration;
import com.google.android.accessibility.talkback.Feedback.VoiceRecognition;
import com.google.android.accessibility.talkback.Feedback.WebAction;
import com.google.android.accessibility.talkback.actor.AutoScrollActor;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.FocusActor;
import com.google.android.accessibility.talkback.actor.FocusActorForScreenStateChange;
import com.google.android.accessibility.talkback.actor.FocusActorForTapAndTouchExploration;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.actor.GestureReporter;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.actor.LanguageActor;
import com.google.android.accessibility.talkback.actor.NodeActionPerformer;
import com.google.android.accessibility.talkback.actor.NumberAdjustor;
import com.google.android.accessibility.talkback.actor.PassThroughModeActor;
import com.google.android.accessibility.talkback.actor.SpeechRateActor;
import com.google.android.accessibility.talkback.actor.SystemActionPerformer;
import com.google.android.accessibility.talkback.actor.TalkBackUIActor;
import com.google.android.accessibility.talkback.actor.TextEditActor;
import com.google.android.accessibility.talkback.actor.VolumeAdjustor;
import com.google.android.accessibility.talkback.actor.search.SearchScreenNodeStrategy;
import com.google.android.accessibility.talkback.actor.voicecommands.SpeechRecognizerActor;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.preference.TalkBackHelpPreferencesActivity;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Pipeline stage for feedback execution. REFERTO */
class Actors {

  private static final String LOG_TAG = "Actors";

  //////////////////////////////////////////////////////////////////////////
  // Member data
  // TODO: Add more actors for braille, UI-actions.

  private final Context context;
  private final DimScreenActor dimmer;
  private final SpeechControllerImpl speaker;
  private final FullScreenReadActor continuousReader;
  private final FeedbackController soundAndVibration;
  private final AutoScrollActor scroller;
  private final FocusActor focuser;
  private final FocusActorForScreenStateChange focuserWindowChange;
  private final FocusActorForTapAndTouchExploration focuserTouch;
  private final DirectionNavigationActor directionNavigator;
  private final SearchScreenNodeStrategy searcher;
  private final TextEditActor editor;
  private final CustomLabelManager labeler;
  private final NodeActionPerformer nodeActionPerformer;
  private final SystemActionPerformer systemActionPerformer;
  private final PassThroughModeActor passThroughModeActor;
  private final LanguageActor languageSwitcher;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final TalkBackUIActor talkBackUIActor;
  private final SpeechRateActor speechRateActor;
  private final NumberAdjustor numberAdjustor;
  private final VolumeAdjustor volumeAdjustor;
  private final ActorStateWritable actorState;
  private final SpeechRecognizerActor speechRecognizer;
  private final GestureReporter gestureReporter;
  private final ImageCaptioner imageCaptioner;

  //////////////////////////////////////////////////////////////////////////
  // Construction methods

  public Actors(
      Context context,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      DimScreenActor dimmer,
      SpeechControllerImpl speaker,
      FullScreenReadActor continuousReader,
      FeedbackController soundAndVibration,
      AutoScrollActor scroller,
      FocusActor focuser,
      FocusActorForScreenStateChange focuserWindowChange,
      FocusActorForTapAndTouchExploration focuserTouch,
      DirectionNavigationActor directionNavigator,
      SearchScreenNodeStrategy searchScreenNodeStrategy,
      TextEditActor editor,
      CustomLabelManager labeler,
      NodeActionPerformer nodeActionPerformer,
      SystemActionPerformer systemActionPerformer,
      LanguageActor languageSwitcher,
      @Nullable PassThroughModeActor passThroughModeActor,
      TalkBackUIActor talkBackUIActor,
      SpeechRateActor speechRateActor,
      NumberAdjustor numberAdjustor,
      VolumeAdjustor volumeAdjustor,
      SpeechRecognizerActor speechRecognizer,
      GestureReporter gestureReporter,
      ImageCaptioner imageCaptioner) {
    this.context = context;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.dimmer = dimmer;
    this.speaker = speaker;
    this.continuousReader = continuousReader;
    this.soundAndVibration = soundAndVibration;
    this.scroller = scroller;
    this.focuser = focuser;
    this.focuserWindowChange = focuserWindowChange;
    this.focuserTouch = focuserTouch;
    this.directionNavigator = directionNavigator;
    searcher = searchScreenNodeStrategy;
    this.editor = editor;
    this.labeler = labeler;
    this.nodeActionPerformer = nodeActionPerformer;
    this.systemActionPerformer = systemActionPerformer;
    this.languageSwitcher = languageSwitcher;
    this.passThroughModeActor = passThroughModeActor;
    this.talkBackUIActor = talkBackUIActor;
    this.speechRateActor = speechRateActor;
    this.numberAdjustor = numberAdjustor;
    this.volumeAdjustor = volumeAdjustor;
    this.speechRecognizer = speechRecognizer;
    this.gestureReporter = gestureReporter;
    this.imageCaptioner = imageCaptioner;

    actorState =
        new ActorStateWritable(
            dimmer.state,
            speaker.state,
            continuousReader.state,
            scroller.stateReader,
            focuser.getHistory(),
            directionNavigator.state,
            nodeActionPerformer.stateReader,
            languageSwitcher.state,
            speechRateActor.state,
            passThroughModeActor.state,
            labeler.stateReader);
    // Focuser stores some actor-state in ActorState, because focuser does not use that state
    // internally, only for communication to interpeters.
    this.focuser.setActorState(actorState);
    ActorState actorStateReadOnly = new ActorState(actorState);
    this.directionNavigator.setActorState(actorStateReadOnly);
    this.focuserWindowChange.setActorState(actorStateReadOnly);
    this.languageSwitcher.setActorState(actorStateReadOnly);
    this.focuserTouch.setActorState(actorStateReadOnly);
    this.imageCaptioner.setActorState(actorStateReadOnly);
  }

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipelineEventReceiver) {
    scroller.setPipelineEventReceiver(pipelineEventReceiver);
  }

  public void setPipelineFeedbackReturner(Pipeline.FeedbackReturner pipelineFeedbackReturner) {
    dimmer.setPipeline(pipelineFeedbackReturner);
    continuousReader.setPipeline(pipelineFeedbackReturner);
    directionNavigator.setPipeline(pipelineFeedbackReturner);
    editor.setPipeline(pipelineFeedbackReturner);
    focuser.setPipeline(pipelineFeedbackReturner);
    focuserWindowChange.setPipeline(pipelineFeedbackReturner);
    languageSwitcher.setPipeline(pipelineFeedbackReturner);
    if (passThroughModeActor != null) {
      passThroughModeActor.setPipeline(pipelineFeedbackReturner);
    }
    focuserTouch.setPipeline(pipelineFeedbackReturner);
    numberAdjustor.setPipeline(pipelineFeedbackReturner);
    speechRecognizer.setPipeline(pipelineFeedbackReturner);
    imageCaptioner.setPipeline(pipelineFeedbackReturner);
  }

  public void setUserInterface(UserInterface userInterface) {
    directionNavigator.setUserInterface(userInterface);
  }

  //////////////////////////////////////////////////////////////////////////
  // Pipeline methods

  /** Returns a read-only actor state data structure. */
  public ActorState getState() {
    return new ActorState(actorState);
  }

  /** Executes feedback and modifies actorState. Returns success flag. */
  public boolean act(@Nullable EventId eventId, Feedback.Part part) {
    LogUtils.d(LOG_TAG, "act() eventId=%s part=%s", eventId, part);

    boolean success = true;

    // Custom labels
    @Nullable Label label = part.label();
    if (label != null && label.node() != null) {
      switch (label.action()) {
        case SET:
          success &=
              labeler.canAddLabel(label.node())
                  && labeler.needsLabel(label.node())
                  && labeler.setLabel(label.node(), label.text());
          break;
      }
    }

    // Continuous reading
    @Nullable ContinuousRead continuousRead = part.continuousRead();
    if (continuousRead != null && continuousRead.action() != null) {
      switch (continuousRead.action()) {
        case START_AT_TOP:
          continuousReader.startReadingFromBeginning(eventId);
          break;
        case START_AT_NEXT:
          continuousReader.startReadingFromNextNode(eventId);
          break;
        case READ_FOCUSED_CONTENT:
          continuousReader.readFocusedContent(eventId);
          break;
        case INTERRUPT:
          continuousReader.interrupt();
          break;
      }
    }

    @Nullable DimScreen dim = part.dimScreen();
    if (dim != null && dim.action() != null) {
      switch (dim.action()) {
        case BRIGHTEN:
          dimmer.disableDimming();
          break;
        case DIM:
          dimmer.enableDimmingAndShowConfirmDialog();
          break;
      }
    }

    // Speech
    @Nullable Speech speech = part.speech();
    if (speech != null && speech.action() != null) {
      switch (speech.action()) {
        case SPEAK:
          if ((speech.hint() != null)
              && (speech.hintSpeakOptions() != null)
              && (speech.hintSpeakOptions().mCompletedAction != null)) {
            speaker.addUtteranceCompleteAction(
                speaker.peekNextUtteranceId(), speech.hintSpeakOptions().mCompletedAction);
          }
          if (speech.text() != null) {
            speaker.speak(speech.text(), eventId, speech.options());
          }
          break;
        case SAVE_LAST:
          speaker.saveLastUtterance();
          break;
        case COPY_SAVED:
          speaker.copySavedUtteranceToClipboard(eventId);
          break;
        case COPY_LAST:
          speaker.copyLastUtteranceToClipboard(eventId);
          break;
        case REPEAT_SAVED:
          speaker.repeatSavedUtterance();
          break;
        case SPELL_SAVED:
          speaker.spellSavedUtterance();
          break;
        case PAUSE_OR_RESUME:
          speaker.pauseOrResumeUtterance();
          break;
        case TOGGLE_VOICE_FEEDBACK:
          speaker.toggleVoiceFeedback();
          break;
        case SILENCE:
          speaker.setSilenceSpeech(true);
          break;
        case UNSILENCE:
          speaker.setSilenceSpeech(false);
      }
    }

    @Nullable VoiceRecognition voiceRecognition = part.voiceRecognition();
    if (voiceRecognition != null && voiceRecognition.action() != null) {
      switch (voiceRecognition.action()) {
        case START_LISTENING:
          speechRecognizer.getSpeechPermissionAndListen(voiceRecognition.checkDialog());
          break;
        case STOP_LISTENING:
          speechRecognizer.stopListening();
          break;
        case SHOW_COMMAND_LIST:
          speechRecognizer.showCommandsHelpPage();
          break;
      }
    }

    // Sound effects
    @Nullable Sound sound = part.sound();
    if (sound != null) {
      soundAndVibration.playAuditory(sound.resourceId(), sound.rate(), sound.volume(), eventId);
    }

    // Vibration
    @Nullable Vibration vibration = part.vibration();
    if (vibration != null) {
      soundAndVibration.playHaptic(vibration.resourceId(), eventId);
    }

    // TriggerIntent
    @Nullable TriggerIntent triggerIntent = part.triggerIntent();
    if (triggerIntent != null) {
      Intent intent = null;
      switch (triggerIntent.action()) {
        case TRIGGER_TUTORIAL:
          intent = new Intent(context, TalkBackHelpPreferencesActivity.class);
          intent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
          break;
        case TRIGGER_PRACTICE_GESTURE:
          intent = TutorialInitiator.createPracticeGesturesIntent(context);
          break;
        case TRIGGER_ASSISTANT:
          // The intent to invoke assistant for watch is different from for phone.
          intent =
              new Intent(FeatureSupport.isWatch(context) ? ACTION_ASSIST : ACTION_VOICE_COMMAND);
          intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP);
          break;
      }
      try {
        if (intent != null) {
          context.startActivity(intent);
        }
      } catch (ActivityNotFoundException exception) {
        LogUtils.d(LOG_TAG, intent + " can not be served by any activity.");
      }
    }

    // Language
    @Nullable Language language = part.language();
    if (language != null && language.action() != null) {
      switch (language.action()) {
        case PREVIOUS_LANGUAGE:
          languageSwitcher.selectPreviousOrNextLanguage(/* isNext= */ false);
          break;
        case NEXT_LANGUAGE:
          languageSwitcher.selectPreviousOrNextLanguage(/* isNext= */ true);
          break;
        case SET_LANGUAGE:
          languageSwitcher.setLanguage(language.currentLanguage());
          break;
      }
    }

    // System action
    @Nullable SystemAction systemAction = part.systemAction();
    if (systemAction != null) {
      success &= systemActionPerformer.performAction(systemAction.systemActionId());
    }

    // Text editing
    @Nullable EditText edit = part.edit();
    if (edit != null) {
      switch (edit.action()) {
        case SELECT_ALL:
          success &= editor.selectAll(edit.node(), eventId);
          break;

        case START_SELECT:
          success &= editor.startSelect(edit.node(), eventId);
          break;

        case END_SELECT:
          success &= editor.endSelect(edit.node(), eventId);
          break;

        case COPY:
          success &= editor.copy(edit.node(), eventId);
          break;

        case CUT:
          success &= editor.cut(edit.node(), eventId);
          break;

        case PASTE:
          success &= editor.paste(edit.node(), eventId);
          break;

        case DELETE:
          success &= editor.delete(edit.node(), eventId);
          break;

        case CURSOR_TO_BEGINNING:
          success &= editor.cursorToBeginning(edit.node(), edit.stopSelecting(), eventId);
          break;

        case CURSOR_TO_END:
          success &= editor.cursorToEnd(edit.node(), edit.stopSelecting(), eventId);
          break;

        case INSERT:
          success &= editor.insert(edit.node(), edit.text(), eventId);
          break;
      }
    }

    // Node action
    @Nullable NodeAction nodeAction = part.nodeAction();
    AccessibilityNode nodeActionTarget = (nodeAction == null) ? null : nodeAction.target();
    if (nodeAction != null && nodeActionTarget != null) {
      success &= nodeActionPerformer.performAction(nodeAction, eventId);
    }

    // Scrolling
    @Nullable Scroll scroll = part.scroll();
    if (scroll != null) {
      switch (scroll.action()) {
        case SCROLL:
          success &=
              scroller.scroll(
                  scroll.userAction(),
                  scroll.node(),
                  scroll.nodeCompat(),
                  scroll.nodeAction(),
                  scroll.source(),
                  scroll.timeout(),
                  eventId);
          break;

        case CANCEL_TIMEOUT:
          scroller.cancelTimeout();
          break;

        case ENSURE_ON_SCREEN:
          success &=
              scroller.ensureOnScreen(
                  scroll.userAction(),
                  scroll.nodeCompat(),
                  scroll.nodeToMoveOnScreen(),
                  scroll.source(),
                  scroll.timeout(),
                  eventId);
          break;

        default:
          // Do nothing.
      }
    }

    // Focus
    @Nullable Focus focus = part.focus();
    if (focus != null && focus.action() != null) {
      switch (focus.action()) {
        case FOCUS:
          if (focus.target() != null) {
            success &=
                focuser.setAccessibilityFocus(
                    focus.target(), focus.forceRefocus(), focus.focusActionInfo(), eventId);
          }
          break;
        case CLEAR:
          focuser.clearAccessibilityFocus(eventId);
          break;
        case CACHE:
          success &= focuser.cacheNodeToRestoreFocus();
          break;
        case MUTE_NEXT_FOCUS:
          focuser.setMuteNextFocus();
          break;
        case RESTORE_ON_NEXT_WINDOW:
          focuser.overrideNextFocusRestorationForContextMenu();
          break;
        case RESTORE:
          success &= focuser.restoreFocus(eventId);
          break;
        case CLEAR_CACHED:
          success &= focuser.popCachedNodeToRestoreFocus();
          break;
        case INITIAL_FOCUS_RESTORE:
          success &= focuserWindowChange.restoreLastFocusedNode(focus.screenState(), eventId);
          break;
        case INITIAL_FOCUS_FOLLOW_INPUT:
          success &=
              focuserWindowChange.syncA11yFocusToInputFocusedEditText(focus.screenState(), eventId);
          break;
        case INITIAL_FOCUS_FIRST_CONTENT:
          success &=
              focuserWindowChange.focusOnFirstFocusableNonTitleNode(focus.screenState(), eventId);
          break;
        case FOCUS_FOR_TOUCH:
          success &=
              focuserTouch.setAccessibilityFocus(focus.target(), focus.forceRefocus(), eventId);
          break;
        case CLICK_NODE:
          success &= focuserTouch.performClick(focus.target(), eventId);
          break;
        case LONG_CLICK_NODE:
          success &= focuserTouch.attemptLongPress(focus.target(), eventId);
          break;
        case CLICK_CURRENT:
          success &= focuser.clickCurrentFocus(eventId);
          break;
        case LONG_CLICK_CURRENT:
          success &= focuser.longClickCurrentFocus(eventId);
          break;
        case CLICK_ANCESTOR:
          success &= focuser.clickCurrentHierarchical(eventId);
          break;
        case SEARCH_FROM_TOP:
          if (focus.searchKeyword() != null) {
            success &=
                searcher.searchAndFocus(
                    /* startAtRoot= */ true, focus.searchKeyword(), directionNavigator);
          }
          break;
        case SEARCH_AGAIN:
          success &=
              searcher.searchAndFocus(
                  /* startAtRoot= */ false, searcher.getLastKeyword(), directionNavigator);
          break;
        case ENSURE_ACCESSIBILITY_FOCUS_ON_SCREEN:
          success &= focuser.ensureAccessibilityFocusOnScreen(eventId);
          break;
      }
    }

    // PassThroughMode
    @Nullable PassThroughMode passThroughMode = part.passThroughMode();
    if (passThroughMode != null && passThroughModeActor != null) {
      switch (passThroughMode.action()) {
        case ENABLE_PASSTHROUGH:
          passThroughModeActor.setTouchExplorePassThrough(/* enable= */ true);
          break;
        case DISABLE_PASSTHROUGH:
          passThroughModeActor.setTouchExplorePassThrough(/* enable= */ false);
          break;
        case PASSTHROUGH_CONFIRM_DIALOG:
          passThroughModeActor.showEducationDialog();
          break;
        case STOP_TIMER:
          passThroughModeActor.cancelPassThroughGuardTimer();
          break;
        case LOCK_PASS_THROUGH:
          passThroughModeActor.lockTouchExplorePassThrough(passThroughMode.region());
          break;
        default:
          break;
      }
    }

    // SpeechRate
    @Nullable SpeechRate speechRate = part.speechRate();
    if (speechRate != null && speechRateActor != null) {
      switch (speechRate.action()) {
        case INCREASE_RATE:
        case DECREASE_RATE:
          success &=
              speechRateActor.changeSpeechRate(
                  /* isIncrease= */ speechRate.action() == INCREASE_RATE);
          break;
        default:
          break;
      }
    }

    // AdjustValue
    @Nullable AdjustValue adjustValue = part.adjustValue();
    if (adjustValue != null) {
      switch (adjustValue.action()) {
        case INCREASE_VALUE:
        case DECREASE_VALUE:
          success &= numberAdjustor.adjustValue(adjustValue.action() == DECREASE_VALUE);
          break;
        default:
          break;
      }
    }

    // VolumeValue
    @Nullable AdjustVolume adjustVolume = part.adjustVolume();
    if (adjustVolume != null) {
      switch (adjustVolume.action()) {
        case INCREASE_VOLUME:
        case DECREASE_VOLUME:
          success &=
              volumeAdjustor.adjustVolume(
                  adjustVolume.action() == DECREASE_VOLUME, adjustVolume.streamType());
          break;
      }
    }

    // FocusDirection
    @Nullable FocusDirection direction = part.focusDirection();
    if (direction != null) {
      switch (direction.action()) {
        case NEXT:
          directionNavigator.navigateWithSpecifiedGranularity(
              SEARCH_FOCUS_FORWARD,
              direction.granularity(),
              direction.wrap(),
              direction.inputMode(),
              eventId);
          break;
        case FOLLOW:
          directionNavigator.followTo(direction.targetNode(), direction.direction(), eventId);
          break;
        case NEXT_PAGE:
          directionNavigator.more(eventId);
          break;
        case PREVIOUS_PAGE:
          directionNavigator.less(eventId);
          break;
        case SCROLL_UP:
          directionNavigator.scrollDirection(eventId, NavigationAction.SCROLL_UP);
          break;
        case SCROLL_DOWN:
          directionNavigator.scrollDirection(eventId, NavigationAction.SCROLL_DOWN);
          break;
        case SCROLL_LEFT:
          directionNavigator.scrollDirection(eventId, NavigationAction.SCROLL_LEFT);
          break;
        case SCROLL_RIGHT:
          directionNavigator.scrollDirection(eventId, NavigationAction.SCROLL_RIGHT);
          break;
        case TOP:
          directionNavigator.jumpToTop(direction.inputMode(), eventId);
          break;
        case BOTTOM:
          directionNavigator.jumpToBottom(direction.inputMode(), eventId);
          break;
        case SET_GRANULARITY:
          directionNavigator.setGranularity(
              direction.granularity(), direction.targetNode(), direction.fromUser(), eventId);
          break;
        case NEXT_GRANULARITY:
          directionNavigator.nextGranularity(eventId);
          break;
        case PREVIOUS_GRANULARITY:
          directionNavigator.previousGranularity(eventId);
          break;
        case SELECTION_MODE_ON:
          directionNavigator.setSelectionModeActive(direction.targetNode(), eventId);
          break;
        case SELECTION_MODE_OFF:
          directionNavigator.setSelectionModeInactive();
          break;

        case NAVIGATE:
          if (direction.toWindow()) {
            success &=
                directionNavigator.navigateToNextOrPreviousWindow(
                    direction.direction(),
                    direction.defaultToInputFocus(),
                    direction.inputMode(),
                    eventId);
          } else if (direction.hasHtmlTargetType()) {
            success &=
                directionNavigator.navigateToHtmlElement(
                    direction.htmlTargetType(),
                    direction.direction(),
                    direction.inputMode(),
                    eventId);
          } else if (direction.granularity() != null) {
            success &=
                directionNavigator.navigateWithSpecifiedGranularity(
                    direction.direction(),
                    direction.granularity(),
                    direction.wrap(),
                    direction.inputMode(),
                    eventId);
          } else {
            success &=
                directionNavigator.navigate(
                    direction.direction(),
                    direction.wrap(),
                    direction.scroll(),
                    direction.defaultToInputFocus(),
                    direction.inputMode(),
                    eventId);
          }
          break;
      }
    }

    // Web action
    @Nullable WebAction webAction = part.webAction();
    if (webAction != null) {
      switch (webAction.action()) {
        case PERFORM_ACTION:
          success &= focuser.getWebActor().performAction(webAction, eventId);
          break;
        case HTML_DIRECTION:
          success &=
              focuser
                  .getWebActor()
                  .navigateToHtmlElement(webAction.target(), webAction.navigationAction(), eventId);
          break;
      }
    }

    // TalkBack UI
    @Nullable TalkBackUI talkBackUI = part.talkBackUI();
    if (talkBackUI != null) {
      switch (talkBackUI.action()) {
        case SHOW_SELECTOR_UI:
          success &=
              talkBackUIActor.showQuickMenu(
                  talkBackUI.type(), talkBackUI.message(), talkBackUI.showIcon());
          break;
        case HIDE:
          success &= talkBackUIActor.hide(talkBackUI.type());
          break;
        case SUPPORT:
          success &= talkBackUIActor.setSupported(talkBackUI.type(), true);
          break;
        case NOT_SUPPORT:
          success &= talkBackUIActor.setSupported(talkBackUI.type(), false);
          break;
      }
    }

    // Show Toast
    @Nullable ShowToast showToast = part.showToast();
    if (showToast != null) {
      switch (showToast.action()) {
        case SHOW:
          Toast.makeText(
                  context,
                  showToast.message(),
                  showToast.durationIsLong() ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)
              .show();
          break;
      }
    }

    // Gesture
    @Nullable Gesture gesture = part.gesture();
    if (gesture != null) {
      switch (gesture.action()) {
        case SAVE:
          success &= gestureReporter.record(gesture.currentGesture());
          break;
        case REPORT:
          success &= gestureReporter.report();
          break;
      }
    }

    // Image caption
    @Nullable ImageCaption imageCaption = part.imageCaption();
    if (imageCaption != null) {
      switch (imageCaption.action()) {
        case PERFORM_CAPTIONS:
          success &=
              imageCaptioner.caption(
                  imageCaption.target(), /* isUserRequested= */ imageCaption.userRequested());
          break;
      }
    }

    // Device info
    @Nullable DeviceInfo deviceInfo = part.deviceInfo();
    if (deviceInfo != null) {
      switch (deviceInfo.action()) {
        case CONFIG_CHANGED:
          success &= talkBackUIActor.onConfigurationChanged();
          break;
      }
    }

    // UI change events
    @Nullable UiChange uiChange = part.uiChange();
    if (uiChange != null) {
      @Nullable Rect sourceBounds = uiChange.sourceBoundsInScreen();
      switch (uiChange.action()) {
        case CLEAR_SCREEN_CACHE:
          if (sourceBounds == null) {
            success &= imageCaptioner.clearWholeScreenCache();
          } else {
            success &= imageCaptioner.clearPartialScreenCache(sourceBounds);
          }
          break;
        case CLEAR_CACHE_FOR_VIEW:
          success &= imageCaptioner.clearCacheForView(sourceBounds);
          break;
      }
    }

    return success;
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Start and stop methods

  public void onBoot(boolean quiet) {
    speaker.updateTtsEngine(quiet);
  }

  public void onUnbind(float finalAnnouncementVolume) {
    // Main thread will be waiting during the TTS announcement, thus in this special case we should
    // not handle TTS callback in main thread.
    speaker.setHandleTtsCallbackInMainThread(false);
    // TalkBack is not allowed to display overlay at this state.
    speaker.setOverlayEnabled(false);
    speaker.setSpeechVolume(finalAnnouncementVolume);
    speaker.setMute(true);
    soundAndVibration.shutdown();
  }

  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {
    speaker.interrupt(stopTtsSpeechCompletely);
    soundAndVibration.interrupt();
  }

  public void interruptSoundAndVibration() {
    soundAndVibration.interrupt();
  }

  /**
   * Interrupts speech, with some exceptions. Does not interrupt:
   *
   * <ul>
   *   <li>When the WebView is active, because the IME is unintentionally dismissed by WebView's
   *       performAction implementation.
   * </ul>
   */
  public void interruptGentle(EventId eventId) {
    @Nullable AccessibilityNodeInfoCompat currentFocus =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
    if (Role.getRole(currentFocus) == Role.ROLE_WEB_VIEW) {
      return;
    }

    if (actorState.continuousRead.isActive()) {
      interruptSoundAndVibration();
    } else {
      interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
    }
  }

  public void shutdown() {
    speaker.shutdown();
  }

  /////////////////////////////////////////////////////////////////////////////////
  // Parameter setting pass-through methods
  // Keeping preference logic outside actors, in specific accessibility-service code.

  public void setOverlayEnabled(boolean enabled) {
    speaker.setOverlayEnabled(enabled);
  }

  public void setUseIntonation(boolean use) {
    speaker.setUseIntonation(use);
  }

  public void setUsePunctuation(boolean use) {
    speaker.setUsePunctuation(use);
  }

  public void setSpeechPitch(float pitch) {
    speaker.setSpeechPitch(pitch);
  }

  public void setSpeechRate(float rate) {
    speaker.setSpeechRate(rate);
  }

  public void setUseAudioFocus(boolean use) {
    speaker.setUseAudioFocus(use);
  }

  public void setSpeechVolume(float volume) {
    speaker.setSpeechVolume(volume);
  }
}
