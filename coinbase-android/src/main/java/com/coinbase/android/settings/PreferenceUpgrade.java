package com.coinbase.android.settings;

import android.app.Application;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import com.coinbase.android.Constants;
import com.coinbase.android.Log;
import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Account;
import com.google.inject.Injector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import roboguice.RoboGuice;

public class PreferenceUpgrade {
  public static final String KEY_ACTIVE_ACCOUNT = "active_account";

  public static final String KEY_ACCOUNT_ACCESS_TOKEN = "account_%d_access_token";
  public static final String KEY_ACCOUNT_REFRESH_TOKEN = "account_%d_refresh_token";
  public static final String KEY_ACCOUNT_TOKEN_EXPIRES_AT = "account_%d_token_expires_at";
  public static final String KEY_ACCOUNT_VALID = "account_%d_valid";
  public static final String KEY_ACCOUNT_VALID_DESC = "account_%d_valid_desc";
  public static final String KEY_ACCOUNT_NAME = "account_%d_name";
  public static final String KEY_ACCOUNT_FULL_NAME = "account_%d_full_name";
  public static final String KEY_ACCOUNT_TIME_ZONE = "account_%d_time_zone";
  public static final String KEY_ACCOUNT_POS_NOTES = "account_%1$d_pos_notes";
  public static final String KEY_ACCOUNT_POS_BTC_AMT = "account_%1$d_pos_btc_amt";
  public static final String KEY_ACCOUNT_SHOW_BALANCE = "account_%1$d_show_balance";
  public static final String KEY_ACCOUNT_FIRST_LAUNCH = "account_%1$d_first_launch";
  public static final String KEY_ACCOUNT_RATE_NOTICE_STATE = "account_%1$d_rate_notice_state";
  public static final String KEY_ACCOUNT_APP_USAGE = "account_%1$d_app_usage";
  public static final String KEY_ACCOUNT_BALANCE_FUZZY = "account_%1$d_balance_fuzzy";
  public static final String KEY_ACCOUNT_TRANSFER_CURRENCY_BTC = "account_%1$d_transfer_currency_btc";
  public static final String KEY_ACCOUNT_ENABLE_TIPPING = "account_%1$d_enable_tipping";
  public static final String KEY_ACCOUNT_LIMIT = "account_%1$d_limit_%2$s";
  public static final String KEY_ACCOUNT_LIMIT_CURRENCY = "account_%1$d_limit_currency_%2$s";
  public static final String KEY_ACCOUNT_NATIVE_CURRENCY = "account_%d_native_currency";

  public static final String KEY_ACCOUNT_PIN = "account_%d_pin";
  public static final String KEY_ACCOUNT_LAST_PIN_ENTRY_TIME = "account_%d_last_pin_entry_time";
  public static final String KEY_ACCOUNT_PIN_VIEW_ALLOWED = "account_%d_pin_view_allowed";

  public static final String KEY_ACCOUNT_ID = "account_%d_id";
  public static final String KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS = "account_%d_enable_merchant_tools";

  public static void perform(Application context) {
    Log.i("PreferenceUpgrade", "In perform");

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
    SharedPreferences.Editor e = prefs.edit();
    int activeAccountIndex = prefs.getInt(KEY_ACTIVE_ACCOUNT, -1);
    if (activeAccountIndex > -1) try {

      if (!prefs.getBoolean(String.format(KEY_ACCOUNT_VALID, activeAccountIndex), true)) {
        throw new Exception("Account invalid, not upgrading");
      }

      Log.i("PreferenceUpgrade", "Upgrading preferences from old version");

      upgradeString(prefs, KEY_ACCOUNT_ACCESS_TOKEN, activeAccountIndex, Constants.KEY_ACCOUNT_ACCESS_TOKEN);
      upgradeString(prefs, KEY_ACCOUNT_REFRESH_TOKEN, activeAccountIndex, Constants.KEY_ACCOUNT_REFRESH_TOKEN);
      upgradeLong(prefs, KEY_ACCOUNT_TOKEN_EXPIRES_AT, activeAccountIndex, Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT);
      upgradeString(prefs, KEY_ACCOUNT_VALID_DESC, activeAccountIndex, Constants.KEY_ACCOUNT_VALID_DESC);
      upgradeString(prefs, KEY_ACCOUNT_NAME, activeAccountIndex, Constants.KEY_ACCOUNT_EMAIL);
      upgradeString(prefs, KEY_ACCOUNT_FULL_NAME, activeAccountIndex, Constants.KEY_ACCOUNT_FULL_NAME);
      upgradeString(prefs, KEY_ACCOUNT_TIME_ZONE, activeAccountIndex, Constants.KEY_ACCOUNT_TIME_ZONE);
      upgradeString(prefs, KEY_ACCOUNT_POS_NOTES, activeAccountIndex, Constants.KEY_ACCOUNT_POS_NOTES);
      upgradeBoolean(prefs, KEY_ACCOUNT_POS_BTC_AMT, activeAccountIndex, Constants.KEY_ACCOUNT_POS_BTC_AMT);
      upgradeBoolean(prefs, KEY_ACCOUNT_SHOW_BALANCE, activeAccountIndex, Constants.KEY_ACCOUNT_SHOW_BALANCE);
      upgradeBoolean(prefs, KEY_ACCOUNT_FIRST_LAUNCH, activeAccountIndex, Constants.KEY_ACCOUNT_FIRST_LAUNCH);
      upgradeString(prefs, KEY_ACCOUNT_RATE_NOTICE_STATE, activeAccountIndex, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE);
      upgradeInt(prefs, KEY_ACCOUNT_APP_USAGE, activeAccountIndex, Constants.KEY_ACCOUNT_APP_USAGE);
      upgradeBoolean(prefs, KEY_ACCOUNT_BALANCE_FUZZY, activeAccountIndex, Constants.KEY_ACCOUNT_BALANCE_FUZZY);
      upgradeBoolean(prefs, KEY_ACCOUNT_TRANSFER_CURRENCY_BTC, activeAccountIndex, Constants.KEY_ACCOUNT_TRANSFER_CURRENCY_BTC);
      upgradeBoolean(prefs, KEY_ACCOUNT_ENABLE_TIPPING, activeAccountIndex, Constants.KEY_ACCOUNT_ENABLE_TIPPING);
      upgradeString(prefs, KEY_ACCOUNT_PIN, activeAccountIndex, Constants.KEY_ACCOUNT_PIN);
      upgradeLong(prefs, KEY_ACCOUNT_LAST_PIN_ENTRY_TIME, activeAccountIndex, Constants.KEY_ACCOUNT_LAST_PIN_ENTRY_TIME);
      upgradeBoolean(prefs, KEY_ACCOUNT_PIN_VIEW_ALLOWED, activeAccountIndex, Constants.KEY_ACCOUNT_PIN_VIEW_ALLOWED);
      upgradeString(prefs, KEY_ACCOUNT_NATIVE_CURRENCY, activeAccountIndex, Constants.KEY_ACCOUNT_NATIVE_CURRENCY);
      upgradeString(prefs, KEY_ACCOUNT_ID, activeAccountIndex, Constants.KEY_USER_ID);
      upgradeBoolean(prefs, KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS, activeAccountIndex, Constants.KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS);

      String buyLimitKey = String.format(KEY_ACCOUNT_LIMIT, activeAccountIndex, "buy");
      String sellLimitKey = String.format(KEY_ACCOUNT_LIMIT, activeAccountIndex, "sell");
      String buyLimitCurrencyKey = String.format(KEY_ACCOUNT_LIMIT_CURRENCY, activeAccountIndex, "buy");
      String sellLimitCurrencyKey = String.format(KEY_ACCOUNT_LIMIT_CURRENCY, activeAccountIndex, "sell");

      if (prefs.contains(buyLimitKey)) {
        String oldValue = prefs.getString(buyLimitKey, null);
        e.putString(Constants.KEY_ACCOUNT_LIMIT_BUY, oldValue);
        e.remove(buyLimitKey);
        e.commit();
      }

      if (prefs.contains(sellLimitKey)) {
        String oldValue = prefs.getString(sellLimitKey, null);
        e.putString(Constants.KEY_ACCOUNT_LIMIT_SELL, oldValue);
        e.remove(sellLimitKey);
        e.commit();
      }

      if (prefs.contains(buyLimitCurrencyKey)) {
        String oldValue = prefs.getString(buyLimitCurrencyKey, null);
        e.putString(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_BUY, oldValue);
        e.remove(buyLimitCurrencyKey);
        e.commit();
      }

      if (prefs.contains(sellLimitCurrencyKey)) {
        String oldValue = prefs.getString(sellLimitCurrencyKey, null);
        e.putString(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_SELL, oldValue);
        e.remove(sellLimitCurrencyKey);
        e.commit();
      }

      Injector i = RoboGuice.getBaseApplicationInjector(context);
      DatabaseManager dbManager = i.getInstance(DatabaseManager.class);
      final LoginManager loginManager = i.getInstance(LoginManager.class);

      // Kludge to run network IO on main thread otherwise upgrade would be impossible
      // The old version has no concept of accounts and no way to find the account id
      final Semaphore sem = new Semaphore(0);
      final List<Account> accounts = new ArrayList<Account>();
      new Thread(new Runnable() {
        public void run() {
          try {
            accounts.addAll(loginManager.getClient(null).getAccounts().getAccounts());
          } catch (Exception ex) {

          }
          sem.release();
          }
      }).start();

      sem.acquire();

      if (accounts.size() == 0) {
        throw new Exception("Could not load accounts");
      }

      SQLiteDatabase db = dbManager.openDatabase();
      try {
        AccountORM.clear(db);
        for (Account account : accounts) {
          if (account.isActive()) {
            AccountORM.insert(db, account);
            if (account.isPrimary()) {
              e.putString(Constants.KEY_ACTIVE_ACCOUNT_ID, account.getId());
              e.putBoolean(Constants.KEY_ACCOUNT_VALID, true);
              e.commit();
            }
          }
        }
        Log.v("PreferenceUpgrade", "Successfully loaded accounts");
      } catch (Exception ex) {
        throw ex;
      } finally {
        dbManager.closeDatabase();
      }

      e.remove(KEY_ACTIVE_ACCOUNT);
      e.commit();

      Log.i("PreferenceUpgrade", "Successfully upgraded preferences");
    } catch (Exception ex) {
      Log.e("PreferenceUpgrade", "Failed to upgrade, giving up and clearing preferences");
      // Failed to upgrade, give up and ask for signin
      ex.printStackTrace();
      e.clear();
      e.commit();
    }
    else {
      Log.i("PreferenceUpgrade", "Missing KEY_ACTIVE_ACCOUNT, already upgraded");
    }
  }

  private static void upgradeString(SharedPreferences prefs, String oldKey, int accountIndex, String newKey) {
    String oldKeyFormatted = String.format(oldKey, accountIndex);
    if (prefs.contains(oldKeyFormatted)) {
      String oldValue = prefs.getString(oldKeyFormatted, null);
      SharedPreferences.Editor e = prefs.edit();
      e.putString(newKey, oldValue);
      e.remove(oldKeyFormatted);
      e.commit();
    }
  }

  private static void upgradeInt(SharedPreferences prefs, String oldKey, int accountIndex, String newKey) {
    String oldKeyFormatted = String.format(oldKey, accountIndex);
    if (prefs.contains(oldKeyFormatted)) {
      int oldValue = prefs.getInt(oldKeyFormatted, 0);
      SharedPreferences.Editor e = prefs.edit();
      e.putInt(newKey, oldValue);
      e.remove(oldKeyFormatted);
      e.commit();
    }
  }

  private static void upgradeLong(SharedPreferences prefs, String oldKey, int accountIndex, String newKey) {
    String oldKeyFormatted = String.format(oldKey, accountIndex);
    if (prefs.contains(oldKeyFormatted)) {
      Long oldValue = prefs.getLong(oldKeyFormatted, 0);
      SharedPreferences.Editor e = prefs.edit();
      e.putLong(newKey, oldValue);
      e.remove(oldKeyFormatted);
      e.commit();
    }
  }

  private static void upgradeBoolean(SharedPreferences prefs, String oldKey, int accountIndex, String newKey) {
    String oldKeyFormatted = String.format(oldKey, accountIndex);
    if (prefs.contains(oldKeyFormatted)) {
      boolean oldValue = prefs.getBoolean(oldKeyFormatted, false);
      SharedPreferences.Editor e = prefs.edit();
      e.putBoolean(newKey, oldValue);
      e.remove(oldKeyFormatted);
      e.commit();
    }
  }

}
