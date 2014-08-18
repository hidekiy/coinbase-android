package com.coinbase.android.pin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;

import com.coinbase.android.Constants;
import com.coinbase.android.MainActivity;
import com.coinbase.android.Utils;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.squareup.otto.Bus;

@Singleton
public class PINManager {

  public static final long PIN_REPROMPT_TIME = 2 * 1000; // Five seconds

  public PINManager () {}

  boolean bad = false;

  private static boolean isQuitPINLock = false;

  @Inject protected Bus mBus;

  /**
   * Should the user be allowed to access protected content?
   * @param context
   * @return false if access is denied (and a PIN reprompt is required)
   */
  public boolean shouldGrantAccess(Context context) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    // Does the user have a PIN?
    boolean hasPin = prefs.getString(Constants.KEY_ACCOUNT_PIN, null) != null;
    if(!hasPin) {
      return true;
    }

    // Is the PIN edit-only?
    boolean pinViewAllowed = prefs.getBoolean(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, false);
    if(pinViewAllowed) {
      return true;
    }

    // Is a reprompt required?
    long timeSinceReprompt = System.currentTimeMillis() - prefs.getLong(Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, -1);
    return timeSinceReprompt < PIN_REPROMPT_TIME;
  }

  /**
   * Should the user be allowed to edit protected content? If not, PIN prompt will be started.
   * @param activity
   * @return true if you should proceed with the edit
   */
  public boolean checkForEditAccess(Activity activity) {

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

    // Does the user have a PIN?
    boolean hasPin = prefs.getString(Constants.KEY_ACCOUNT_PIN, null) != null;
    if(!hasPin) {
      return true;
    }

    // Is the PIN edit-only?
    boolean pinViewAllowed = prefs.getBoolean(Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED, false);
    if(!pinViewAllowed) {
      // Still prompt for edits even if view is protected...
      // return true;
    }

    // Is a reprompt required?
    long timeSinceReprompt = System.currentTimeMillis() - prefs.getLong(Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, -1);
    boolean repromptRequired = timeSinceReprompt > PIN_REPROMPT_TIME;

    if(repromptRequired) {

      Intent intent = new Intent(activity, PINPromptActivity.class);
      intent.setAction(PINPromptActivity.ACTION_PROMPT);
      activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_PIN);
      return false;
    } else {
      return true;
    }
  }

  /**
   * Called after the user has entered the PIN successfully.
   */
  public void resetPinClock(Context context) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Editor e = prefs.edit();
    e.putLong(Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, System.currentTimeMillis());
    e.commit();
  }

  /**
   * Set the user's PIN.
   */
  public void setPin(Context context, String pin) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    Editor e = prefs.edit();
    e.putString(Constants.KEY_ACCOUNT_PIN, pin);
    e.commit();
    mBus.post(new UserDataUpdatedEvent());
  }

  /**
   * Set quitting PIN Lock.
   */
  public void setQuitPINLock(boolean quitPINLock) {
      isQuitPINLock = quitPINLock;
  }

  /**
   * Return whether user wants to quit PIN Lock.
   */
  public boolean isQuitPINLock() {
      return isQuitPINLock;
  }
}
