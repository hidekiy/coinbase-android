package com.coinbase.android.task;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import com.coinbase.android.Constants;
import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.event.ReceiveAddressUpdatedEvent;
import com.coinbase.android.task.ApiTask;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

public class GenerateReceiveAddressTask extends ApiTask<String> {
  @Inject protected Bus mBus;
  @Inject protected DatabaseManager mDbManager;

  public GenerateReceiveAddressTask(Context context) {
    super(context);
  }

  @Override
  public String call() throws Exception {
    String newAddress = getClient().generateReceiveAddress().getAddress();

    String accountId = mLoginManager.getActiveAccountId();
    SQLiteDatabase db = mDbManager.openDatabase();
    try {
      AccountORM.setReceiveAddress(db, accountId, newAddress);
    } finally {
      mDbManager.closeDatabase();
    }

    return newAddress;
  }

  @Override
  public void onFinally() {
    mBus.post(new ReceiveAddressUpdatedEvent());
  }
}
