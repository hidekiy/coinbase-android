package com.coinbase.android.transfers;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.coinbase.android.MainActivity;
import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.db.DelayedTransactionORM;
import com.coinbase.android.db.TransactionORM;
import com.coinbase.android.event.RefreshRequestedEvent;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Account;
import com.coinbase.api.entity.Transaction;
import com.coinbase.api.exception.CoinbaseException;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import roboguice.service.RoboService;

public class DelayedTxSenderService extends RoboService {
  @Inject
  private Bus mBus;

  @Inject
  private LoginManager mLoginManager;

  @Inject
  private DatabaseManager mDbManager;

  private static int NOTIF_ID = -1;

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  boolean threadRunning = false;

  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!threadRunning) {
      new Thread(new Runnable() {
        public void run() {
          tryToSendAll();
          stopSelf();
        }
      }).start();
    }
    return START_NOT_STICKY;
  }

  private void tryToSendAll() {
    // Check database for delayed TX:
    List<Transaction> delayedTransactions = new ArrayList<Transaction>();
    SQLiteDatabase db = mDbManager.openDatabase();
    try {
      db.beginTransaction();
      List<Account> accounts = AccountORM.list(db);
      for (Account account : accounts) {
        delayedTransactions.addAll(DelayedTransactionORM.getTransactions(db, account.getId()));
      }

      // Attempt to send the delayed TX:
      Log.i("Coinbase", "Sending " + delayedTransactions.size() + " delayed TX now...");
      int successfullySent = 0;
      for (Transaction tx : delayedTransactions) {
        try {
          if (tx.isRequest()) {
            mLoginManager.getClient().requestMoney(tx);
          } else {
            mLoginManager.getClient().sendMoney(tx);
          }
          successfullySent++;
          showNotification(null, tx);
          DelayedTransactionORM.delete(db, tx);
        } catch (CoinbaseException cbEx) {
          Log.e("DelayedTxSenderService", "Failed to send delayed tx", cbEx);
          showNotification(cbEx.getMessage(), tx);
        } catch (Exception ex) {
          Log.e("DelayedTxSenderService", "Failed to send delayed tx", ex);
        }
      }

      db.setTransactionSuccessful();

      Handler handler = new Handler(Looper.getMainLooper());
      handler.post(new Runnable() {
        @Override
        public void run() {
          mBus.post(new RefreshRequestedEvent());
        }
      });

      // Disable the broadcast receiver if all transactions were successfully sent.
      if (successfullySent == delayedTransactions.size()) {
        PackageManager pm = getPackageManager();
        ComponentName br = new ComponentName(this, ConnectivityChangeReceiver.class);
        pm.setComponentEnabledSetting(br, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
      }
    } finally {
      db.endTransaction();
      mDbManager.closeDatabase();
    }
  }

  private void showNotification(String errors, Transaction tx) {
    String title, description;

    if (errors == null) {
      if (tx.isRequest()) {
        title = getString(R.string.delayed_notification_success_request);
      } else {
        title = getString(R.string.delayed_notification_success_send);
      }
      description = getString(R.string.delayed_notification_success_subtitle);
    } else {
      if (tx.isRequest()) {
        title = getString(R.string.delayed_notification_failure_request);
      } else {
        title = getString(R.string.delayed_notification_failure_send);
      }
      description = errors;
    }

    String otherUser = tx.isRequest() ? tx.getFrom() : tx.getTo();

    title = String.format(title, otherUser, Utils.formatMoney(tx.getAmount()));

    NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(this)
                    .setSmallIcon(R.drawable.ic_notif_delayed)
                    .setContentTitle(title)
                    .setContentText(description)
                    .setAutoCancel(true);
    Intent resultIntent = new Intent(this, MainActivity.class);
    PendingIntent resultPendingIntent = PendingIntent.getActivity(this, 0, resultIntent, 0);
    mBuilder.setContentIntent(resultPendingIntent);
    NotificationManager mNotificationManager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    if (NOTIF_ID == -1) {
      NOTIF_ID = new Random().nextInt();
    }
    mNotificationManager.notify(NOTIF_ID++, mBuilder.build());
  }
}
