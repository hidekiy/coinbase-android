package com.coinbase.android;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import android.content.Intent;
import android.os.Bundle;

import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.bugsnag.android.Bugsnag;
import com.coinbase.android.pin.PINManager;
import com.coinbase.android.pin.PINPromptActivity;
import com.coinbase.api.LoginManager;
import com.github.rtyley.android.sherlock.roboguice.activity.RoboSherlockFragmentActivity;
import com.google.inject.Inject;

public class CoinbaseActivity extends RoboSherlockFragmentActivity {

  /** This activity requires authentication */
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface RequiresAuthentication { }

  /** This activity requires PIN entry (if PIN is enabled) */
  @Retention(RetentionPolicy.RUNTIME)
  public static @interface RequiresPIN { }

  @Inject
  protected LoginManager mLoginManager;

  @Inject
  protected PINManager mPinManager;

  @Override
  public void onPause() {
    super.onPause();
    Bugsnag.onActivityPause(this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Bugsnag.onActivityDestroy(this);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Bugsnag.onActivityCreate(this);
    if(getClass().isAnnotationPresent(RequiresAuthentication.class)) {
      // Check authentication status
      if(!mLoginManager.isSignedIn()) {
        // Not signed in - open login activity.
        redirectToLoginPage();
      }
    }
  }

  @Override
  public void onResume() {

    super.onResume();
    Bugsnag.onActivityResume(this);
    if(getClass().isAnnotationPresent(RequiresAuthentication.class)) {
      // Check authentication status
      if(!mLoginManager.isSignedIn()) {
        // Not signed in - open login activity.
        redirectToLoginPage();
      }
    }

    if(getClass().isAnnotationPresent(RequiresPIN.class)) {
      // Check PIN status
      if(!mPinManager.shouldGrantAccess(this)) {
        // Check if user wants to quit PIN lock
        if(mPinManager.isQuitPINLock()){
          mPinManager.setQuitPINLock(false);
          finish();
        } else {
          // PIN reprompt required.
          Intent intent = new Intent(this, PINPromptActivity.class);
          intent.setAction(PINPromptActivity.ACTION_PROMPT);
          startActivity(intent);
        }
      }
    }

    super.onResume();
  }

  protected void redirectToLoginPage() {
    Intent intent = new Intent(this, LoginActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
    startActivity(intent);
    finish();
  }
}
