package com.coinbase.android.settings;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.coinbase.android.Constants;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.joda.money.CurrencyUnit;

@Singleton
public class ProductionPreferencesManager implements PreferencesManager {

  @Inject
  private Application mContext;

  @Override
  public CurrencyUnit getNativeCurrency() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    String currencyCode = prefs.getString(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, "USD");
    return CurrencyUnit.of(currencyCode);
  }

  void setNativeCurrency(CurrencyUnit currency) {
    SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
    editor.putString(
            Constants.KEY_ACCOUNT_NATIVE_CURRENCY,
            currency.getCurrencyCode()
    );
    editor.commit();
  }

  @Override
  public boolean isTippingEnabled() {
    return getPrefsBool(Constants.KEY_ACCOUNT_ENABLE_TIPPING, false);
  }

  @Override
  public void setTippingEnabled(boolean enabled) {
    putPrefsBool(Constants.KEY_ACCOUNT_ENABLE_TIPPING, enabled);
  }

  @Override
  public boolean posUsesBtc() {
    return getPrefsBool(Constants.KEY_ACCOUNT_POS_BTC_AMT, false);
  }

  @Override
  public void setPosUsesBtc(boolean enabled) {
    putPrefsBool(Constants.KEY_ACCOUNT_POS_BTC_AMT, enabled);
  }

  @Override
  public String getSavedMerchantNotes() {
    return getPrefsString(Constants.KEY_ACCOUNT_POS_NOTES, "");
  }

  @Override
  public void saveMerchantNotes(String notes) {
    putPrefsString(Constants.KEY_ACCOUNT_POS_NOTES, notes);
  }

  public boolean getPrefsBool(String key, boolean def) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getBoolean(key, def);
  }

  public String getPrefsString(String key, String def) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getString(key, def);
  }

  public boolean putPrefsString(String key, String newValue) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.edit().putString(key, newValue).commit();
  }

  public int getPrefsInt(String key, int def) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getInt(key, def);
  }

  public boolean togglePrefsBool(String key, boolean def) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    boolean current = prefs.getBoolean(key, def);
    prefs.edit().putBoolean(key, !current).commit();
    return !current;
  }

  public boolean putPrefsBool(String key, boolean newValue) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.edit().putBoolean(key, newValue).commit();
  }
}
