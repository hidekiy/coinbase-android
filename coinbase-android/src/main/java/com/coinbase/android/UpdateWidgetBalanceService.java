package com.coinbase.android;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.preference.PreferenceManager;

import com.coinbase.api.LoginManager;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;

import org.joda.money.Money;

import java.io.IOException;

import roboguice.service.RoboService;

public class UpdateWidgetBalanceService extends RoboService {

  public static interface WidgetUpdater {
    public void updateWidget(Context context, AppWidgetManager manager, int appWidgetId, String balance);
  }

  public static String EXTRA_WIDGET_ID = "widget_id";
  public static String EXTRA_UPDATER_CLASS = "updater_class";

  @Inject
  private LoginManager mLoginManager;

  @Override
  public int onStartCommand(Intent intent, int flags, final int startId) {

    final int widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1);
    final Class<?> updaterClass = (Class<?>) intent.getSerializableExtra(EXTRA_UPDATER_CLASS);

    new Thread(new Runnable() {
      public void run() {

        try {

          String accountId = PreferenceManager.getDefaultSharedPreferences(UpdateWidgetBalanceService.this).getString(
                  String.format(Constants.KEY_WIDGET_ACCOUNT, widgetId), null);


          // Step 1: Update widget without balance
          AppWidgetManager manager = AppWidgetManager.getInstance(UpdateWidgetBalanceService.this);
          WidgetUpdater updater = (WidgetUpdater) updaterClass.newInstance();
          updater.updateWidget(UpdateWidgetBalanceService.this, manager, widgetId, null);

          // Step 2: Fetch balance for primary account
          String balanceText;
          if(accountId == null) {
            balanceText = "";
          } else {
            balanceText = "";
            Log.i("Coinbase", "Service fetching balance... [" + updaterClass.getSimpleName() + "]");
            Money balance = mLoginManager.getClient(accountId).getBalance();
            balanceText = Utils.formatMoney(balance);
          }

          // Step 3: Update widget
          updater.updateWidget(UpdateWidgetBalanceService.this, manager, widgetId, balanceText);

        } catch(CoinbaseException e) {
          e.printStackTrace();
        } catch (InstantiationException e) {
          e.printStackTrace();
        } catch (IllegalAccessException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }

        stopSelf(startId);
      }
    }).start();

    return Service.START_REDELIVER_INTENT;
  }



  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

}
