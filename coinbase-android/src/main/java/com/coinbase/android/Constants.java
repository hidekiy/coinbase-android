package com.coinbase.android;

public class Constants {

  private Constants() {}

  public static enum RateNoticeState {
    NOTICE_NOT_YET_SHOWN,
    SHOULD_SHOW_NOTICE,
    NOTICE_DISMISSED;
  }

  public static final String KEY_USER_ID = "user_id";
  public static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
  public static final String KEY_KIOSK_MODE = "kiosk_mode";
  
  public static final String KEY_WIDGET_ACCOUNT = "widget_%d_account";
  public static final String KEY_WIDGET_CURRENCY = "widget_%d_currency";
  
  public static final String KEY_ACCOUNT_ACCESS_TOKEN = "account_access_token";
  public static final String KEY_ACCOUNT_REFRESH_TOKEN = "account_refresh_token";
  public static final String KEY_ACCOUNT_TOKEN_EXPIRES_AT = "account_token_expires_at";
  public static final String KEY_ACCOUNT_VALID = "account_valid";
  public static final String KEY_ACCOUNT_VALID_DESC = "account_valid_desc";
  public static final String KEY_ACCOUNT_EMAIL = "account_email";
  public static final String KEY_ACCOUNT_NATIVE_CURRENCY = "account_native_currency";
  public static final String KEY_ACCOUNT_FULL_NAME = "account_full_name";
  public static final String KEY_ACCOUNT_TIME_ZONE = "account_time_zone";
  public static final String KEY_ACCOUNT_LIMIT_SELL = "account_limit_sell";
  public static final String KEY_ACCOUNT_LIMIT_BUY = "account_limit_buy";
  public static final String KEY_ACCOUNT_LIMIT_CURRENCY_BUY = "account_limit_currency_buy";
  public static final String KEY_ACCOUNT_LIMIT_CURRENCY_SELL = "account_limit_currency_sell";
  public static final String KEY_ACCOUNT_POS_NOTES = "account_pos_notes";
  public static final String KEY_ACCOUNT_POS_BTC_AMT = "account_pos_btc_amt";
  public static final String KEY_ACCOUNT_SHOW_BALANCE = "account_show_balance";
  public static final String KEY_ACCOUNT_FIRST_LAUNCH = "account_first_launch";
  public static final String KEY_ACCOUNT_RATE_NOTICE_STATE = "account_rate_notice_state";
  public static final String KEY_ACCOUNT_APP_USAGE = "account_app_usage";
  public static final String KEY_ACCOUNT_BALANCE_FUZZY = "account_balance_fuzzy";
  public static final String KEY_ACCOUNT_TRANSFER_CURRENCY_BTC = "account_transfer_currency_btc";
  public static final String KEY_ACCOUNT_ENABLE_TIPPING = "account_enable_tipping";

  public static final String KEY_ACCOUNT_PIN = "account_pin";
  public static final String KEY_ACCOUNT_SALT = "account_salt";
  public static final String KEY_ACCOUNT_LAST_PIN_ENTRY_TIME = "account_last_pin_entry_time";
  public static final String KEY_ACCOUNT_PIN_VIEW_ALLOWED = "account_pin_view_allowed";
  public static final String KEY_ACCOUNT_ENABLE_MERCHANT_TOOLS = "account_enable_merchant_tools";

}
