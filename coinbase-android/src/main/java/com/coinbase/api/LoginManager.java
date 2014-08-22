package com.coinbase.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.coinbase.api.entity.Account;

import org.joda.money.CurrencyUnit;

import java.util.List;

public interface LoginManager {
  String getClientBaseUrl();
  List<Account> getAccounts();
  boolean switchActiveAccount(Account account);

  boolean needToRefreshAccessToken();

  void refreshAccessToken();

  // start three legged oauth handshake
  String generateOAuthUrl(String redirectUrl);

  String signin(Context context, String code, String originalRedirectUrl);

  String getSelectedAccountName();

  void setAccountValid(boolean status, String desc);

  String getAccountValid();

  // TODO remove methods that take context and use injected context

  String getActiveUserId();

  Coinbase getClient();

  boolean isSignedIn();

  String getReceiveAddress();

  String getActiveAccountId();

  void signout();
}
