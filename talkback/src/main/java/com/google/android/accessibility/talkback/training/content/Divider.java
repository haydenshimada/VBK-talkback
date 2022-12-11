package com.google.android.accessibility.talkback.training.content;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.TrainingIpcClient.ServiceData;

/** A line as a divider. */
public class Divider extends PageContentConfig {

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    return inflater.inflate(R.layout.training_divider, container, false);
  }
}
