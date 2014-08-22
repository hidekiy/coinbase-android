package com.coinbase.android.settings;

import com.google.inject.AbstractModule;

public class PreferencesManagerModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(PreferencesManager.class).to(ProductionPreferencesManager.class);
  }
}
