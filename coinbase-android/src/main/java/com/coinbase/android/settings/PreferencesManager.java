package com.coinbase.android.settings;

import org.joda.money.CurrencyUnit;

/**
 * Created by aianus on 8/22/14.
 */
public interface PreferencesManager {
  CurrencyUnit getNativeCurrency();

  boolean isTippingEnabled();

  void setTippingEnabled(boolean enabled);

  boolean posUsesBtc();

  void setPosUsesBtc(boolean enabled);

  String getSavedMerchantNotes();

  void saveMerchantNotes(String notes);
}
