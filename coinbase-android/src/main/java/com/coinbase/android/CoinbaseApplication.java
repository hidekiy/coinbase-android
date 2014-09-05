package com.coinbase.android;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.bugsnag.android.Bugsnag;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;

import android.app.Application;

public class CoinbaseApplication extends Application {

  @Override
  public void onCreate() {
    Bugsnag.register(this, "79f470735061b474d6fd0a874200557a");
    Bugsnag.setEndpoint("exceptions.coinbase.com:8443");
    Bugsnag.setUseSSL(true);

    super.onCreate();
  }

  private List<MainActivity> mMainActivities = new ArrayList<MainActivity>();

  public void addMainActivity(MainActivity mainActivity) {
    mMainActivities.add(mainActivity);
  }

  public void removeMainActivity(MainActivity mainActivity) {
    mMainActivities.remove(mainActivity);
  }

  public void onDbChange() {
    for (MainActivity m : mMainActivities) {
      m.getTransactionsFragment().loadTransactionsList();;
    }
  }
}
