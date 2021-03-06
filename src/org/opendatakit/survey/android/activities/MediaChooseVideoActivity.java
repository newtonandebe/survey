/*
 * Copyright (C) 2012-2013 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.survey.android.activities;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.opendatakit.common.android.utilities.MediaUtils;
import org.opendatakit.common.android.utilities.ODKFileUtils;
import org.opendatakit.common.android.utilities.WebLogger;
import org.opendatakit.survey.android.R;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Video;
import android.widget.Toast;

/**
 * Simple shim for media interactions.
 *
 * @author mitchellsundt@gmail.com
 *
 */
public class MediaChooseVideoActivity extends Activity {
  private static final String t = "MediaChooseVideoActivity";
  private static final int ACTION_CODE = 1;
  private static final String MEDIA_CLASS = "video/";
  private static final String APP_NAME = "appName";
  private static final String URI_FRAGMENT = "uriFragment";

  private static final String URI_FRAGMENT_NEW_FILE_BASE = "uriFragmentNewFileBase";
  private static final String CONTENT_TYPE = "contentType";

  private String appName = null;
  private String uriFragmentNewFileBase = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Bundle extras = getIntent().getExtras();
    if (extras != null) {
      appName = extras.getString(APP_NAME);
      uriFragmentNewFileBase = extras.getString(URI_FRAGMENT_NEW_FILE_BASE);
    }

    if (savedInstanceState != null) {
      appName = savedInstanceState.getString(APP_NAME);
      uriFragmentNewFileBase = savedInstanceState.getString(URI_FRAGMENT_NEW_FILE_BASE);
    }

    if (appName == null) {
      throw new IllegalArgumentException("Expected " + APP_NAME
            + " key in intent bundle. Not found.");
    }

    if (uriFragmentNewFileBase == null) {
      throw new IllegalArgumentException("Expected " + URI_FRAGMENT_NEW_FILE_BASE
          + " key in intent bundle. Not found.");
    }

    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.setType(MEDIA_CLASS + "*");
    try {
      startActivityForResult(i, ACTION_CODE);
    } catch (ActivityNotFoundException e) {
      Toast.makeText(this,
          getString(R.string.activity_not_found, Intent.ACTION_GET_CONTENT + " " + MEDIA_CLASS),
          Toast.LENGTH_SHORT).show();
      setResult(Activity.RESULT_CANCELED);
      finish();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(APP_NAME, appName);
    outState.putString(URI_FRAGMENT_NEW_FILE_BASE, uriFragmentNewFileBase);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {

    if (resultCode == Activity.RESULT_CANCELED) {
      // request was canceled -- propagate
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    /*
     * We have chosen a saved audio clip from somewhere, but we really want it
     * to be in: /sdcard/odk/instances/[current instance]/something.3gpp so we
     * copy it there and insert that copy into the content provider.
     */

    // get gp of chosen file
    Uri selectedMedia = intent.getData();
    String sourceMediaPath = MediaUtils.getPathFromUri(this, selectedMedia, Video.Media.DATA);
    File sourceMedia = new File(sourceMediaPath);
    String extension = sourceMediaPath.substring(sourceMediaPath.lastIndexOf("."));

    File newMedia = ODKFileUtils.getAsFile(appName, uriFragmentNewFileBase + extension);
    try {
      FileUtils.copyFile(sourceMedia, newMedia);
    } catch (IOException e) {
      WebLogger.getLogger(appName).e(t, "Failed to copy " + sourceMedia.getAbsolutePath());
      Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT).show();
      // keep the image as a captured image so user can choose it.
      setResult(Activity.RESULT_CANCELED);
      finish();
      return;
    }

    WebLogger.getLogger(appName).i(t, "copied " + sourceMedia.getAbsolutePath() + " to " + newMedia.getAbsolutePath());

    Uri mediaURI = null;
    if (newMedia.exists()) {
      // Add the new image to the Media content provider so that the
      // viewing is fast in Android 2.0+
      ContentValues values = new ContentValues(6);
      values.put(Video.Media.TITLE, newMedia.getName());
      values.put(Video.Media.DISPLAY_NAME, newMedia.getName());
      values.put(Video.Media.DATE_ADDED, System.currentTimeMillis());
      values.put(Video.Media.MIME_TYPE, MEDIA_CLASS + extension.substring(1));
      values.put(Video.Media.DATA, newMedia.getAbsolutePath());

      mediaURI = getContentResolver().insert(Video.Media.EXTERNAL_CONTENT_URI, values);
      WebLogger.getLogger(appName).i(t, "Insert " + MEDIA_CLASS + " returned uri = " + mediaURI.toString());

      // if you are replacing an answer. delete the previous image using
      // the
      // content provider.
      String binarypath = MediaUtils.getPathFromUri(this, mediaURI, Video.Media.DATA);
      File newMediaFromCP = new File(binarypath);

      WebLogger.getLogger(appName).i(t, "Return mediaFile: " + newMediaFromCP.getAbsolutePath());
      Intent i = new Intent();
      i.putExtra(URI_FRAGMENT, ODKFileUtils.asUriFragment(appName, newMediaFromCP));
      String name = newMediaFromCP.getName();
      i.putExtra(CONTENT_TYPE, MEDIA_CLASS + name.substring(name.lastIndexOf(".") + 1));
      setResult(Activity.RESULT_OK, i);
      finish();
    } else {
      WebLogger.getLogger(appName).e(t, "No " + MEDIA_CLASS + " exists at: " + newMedia.getAbsolutePath());
      Toast.makeText(this, R.string.media_save_failed, Toast.LENGTH_SHORT).show();
      setResult(Activity.RESULT_CANCELED);
      finish();
    }
  }

}
