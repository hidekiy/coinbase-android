package com.coinbase.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.coinbase.android.task.ApiTask;
import com.coinbase.android.Constants;
import com.coinbase.android.event.UserDataUpdatedEvent;
import com.coinbase.api.entity.User;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

class RefreshSettingsTask extends ApiTask<User> {

  @Inject
  protected Bus mBus;

  public RefreshSettingsTask(Context context) {
    super(context);
  }

  @Override
  public User call() throws Exception {
    return getClient().getUser();
  }

  @Override
  public void onSuccess(User user) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

    SharedPreferences.Editor e = prefs.edit();

    e.putString(Constants.KEY_ACCOUNT_EMAIL, user.getEmail());
    e.putString(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, user.getNativeCurrency().getCurrencyCode());
    e.putString(Constants.KEY_ACCOUNT_FULL_NAME, user.getName());
    e.putString(Constants.KEY_ACCOUNT_TIME_ZONE, user.getTimeZone());
    e.putString(Constants.KEY_ACCOUNT_LIMIT_BUY, user.getBuyLimit().getAmount().toString());
    e.putString(Constants.KEY_ACCOUNT_LIMIT_SELL, user.getSellLimit().getAmount().toString());
    e.putString(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_BUY, user.getBuyLimit().getCurrencyUnit().getCurrencyCode());
    e.putString(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_SELL, user.getSellLimit().getCurrencyUnit().getCurrencyCode());

    e.commit();
  }

  @Override
  protected void onFinally() {
    mBus.post(new UserDataUpdatedEvent());
  }

}
