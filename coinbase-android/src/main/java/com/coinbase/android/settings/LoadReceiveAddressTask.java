package com.coinbase.android.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.task.ApiTask;
import com.coinbase.android.Constants;
import com.coinbase.api.entity.Address;
import com.coinbase.api.entity.AddressesResponse;
import com.google.inject.Inject;

class LoadReceiveAddressTask extends ApiTask<Address> {
  @Inject
  protected DatabaseManager mDbManager;

  public LoadReceiveAddressTask(Context context) {
    super(context);
  }

  @Override
  public Address call() throws Exception {
    Address result = null;

    AddressesResponse response = getClient().getAddresses();
    if (response.getTotalCount() > 0) {
      result = response.getAddresses().get(0);
    }

    return result;
  }

  @Override
  public void onSuccess(Address address) {
    if(address != null) {
      String accountId = mLoginManager.getActiveAccountId();
      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        AccountORM.setReceiveAddress(db, accountId, address.getAddress());
      } finally {
        mDbManager.closeDatabase();
      }
    }
  }
}
