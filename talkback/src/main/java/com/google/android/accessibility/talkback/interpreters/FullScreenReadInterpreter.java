/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.talkback.interpreters;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED;
import static com.google.android.accessibility.talkback.Interpretation.ID.Value.CONTINUOUS_READ_CONTENT_FOCUSED;
import static com.google.android.accessibility.talkback.Interpretation.ID.Value.CONTINUOUS_READ_INTERRUPT;

import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Manages state related to reading the screen from top or next. */
// TODO: Split event-interpreter from feedback-mapper.
public class FullScreenReadInterpreter implements AccessibilityEventListener {
  private static final String LOG_TAG = "FullScreenReadInterpreter";
  // Delay, so that it skip click event from FullScreenReadDialog after accessibility focused event
  // from enhanced focus operation.
  private static final int INTERRUPT_DELAY_MS = 200;
  private long continuousReadStartTime = 0; // Last time continuous read content focused

  /** Event types that should interrupt continuous reading, if active. */
  private static final int MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS =
      TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
          | AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START
          | AccessibilityEventCompat.TYPE_ANNOUNCEMENT;

  private Pipeline.InterpretationReceiver pipeline;
  private ActorState actorState;

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipeline(Pipeline.InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS
        | TYPE_VIEW_ACCESSIBILITY_FOCUSED
        | TYPE_VIEW_SELECTED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {

    if (event.getEventType() == TYPE_VIEW_ACCESSIBILITY_FOCUSED
        && actorState.getContinuousRead().isWaitingForContentFocus()) {
      continuousReadStartTime = event.getEventTime();
      // Focused a content-window node, ready to start reading.
      pipeline.input(eventId, event, new Interpretation.ID(CONTINUOUS_READ_CONTENT_FOCUSED));
    }

    if (actorState.getContinuousRead().isActive()) {
      if (event.getEventType() == TYPE_VIEW_CLICKED
          && event.getEventTime() - continuousReadStartTime < INTERRUPT_DELAY_MS) {
        // Ideally, the receiving of masked accessibility events after user confirms the dialog will
        // interrupt the running Continuous Reading Mode. Sometimes the clicking the OK button event
        // comes so late after the ACM is active, so that TalkBack will stop the continuity. We just
        // filtered out such event in the time window of INTERRUPT_DELAY_MS.
        LogUtils.i(
            LOG_TAG, "Skip event because continuous read focused is working recently: %s", event);
        return;
      }
      if (event.getEventType() == TYPE_VIEW_SELECTED) {
        // Interrupts full screen reading on TYPE_VIEW_SELECTED event that comes from user actions.
        // Except for progress bar and seek bar that may send the event automatically.
        AccessibilityNodeInfo source = event.getSource();
        if (Role.getRole(source) != Role.ROLE_PROGRESS_BAR
            && Role.getRole(source) != Role.ROLE_SEEK_CONTROL) {
          pipeline.input(eventId, event, new Interpretation.ID(CONTINUOUS_READ_INTERRUPT));
        }
      } else if (AccessibilityEventUtils.eventMatchesAnyType(
          event, MASK_EVENT_TYPES_INTERRUPT_CONTINUOUS)) {
        // Interrupts full screen reading on user-initiated events -- not for events generated by
        // automated cursor movement or from delayed user interaction.
        pipeline.input(eventId, event, new Interpretation.ID(CONTINUOUS_READ_INTERRUPT));
      }
    }
  }

}
