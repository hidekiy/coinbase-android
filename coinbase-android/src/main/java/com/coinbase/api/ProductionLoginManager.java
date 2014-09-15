package com.coinbase.api;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.preference.PreferenceManager;

import com.coinbase.android.BuildConfig;
import com.coinbase.android.BuildType;
import com.coinbase.android.Constants;
import com.coinbase.android.Log;
import com.coinbase.android.R;
import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.ClientCacheDatabase;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.api.entity.Account;
import com.coinbase.api.entity.User;
import com.coinbase.api.exception.UnauthorizedException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
public class ProductionLoginManager implements LoginManager {

  // production
  protected static final String CLIENT_ID = BuildConfig.type == BuildType.CONSUMER ? "34183b03a3e1f0b74ee6aa8a6150e90125de2d6c1ee4ff7880c2b7e6e98b11f5" : "82f3e52bb25da3688066a45ec740a1efa686646bcdb89a054b2264bc362d9332";
  protected static final String CLIENT_SECRET = BuildConfig.type == BuildType.CONSUMER ? "2c481f46f9dc046b4b9a67e630041b9906c023d139fbc77a47053328b9d3122d" : "f8d57dceb5a4e36b30318e6f035ad3c846cb4dea18ff4f353a35608f1acb12cf";
  protected static final String CLIENT_BASEURL = "https://coinbase.com:443";

  // development (adjust to your setup)
  //protected static final String CLIENT_ID = "b6753e48f7eff4ca287dd081a251c3801037fcda51bb52181d06947d1fb4cb08";
  //protected static final String CLIENT_SECRET = "da853dce0fcc753501e6fe7972ad64c8525f552e708b585c46e65c12e0a5ef44";
  //public static final String CLIENT_BASEURL = "http://192.168.1.10:3001";

  @Inject
  protected Application mContext;

  @Inject
  protected DatabaseManager dbManager;

  public ProductionLoginManager() {}

  protected String getClientId() {
    return CLIENT_ID;
  }

  @Override
  public String getClientBaseUrl() {
    return CLIENT_BASEURL;
  }

  @Override
  public boolean isSignedIn() {
    return getAccountValid() == null;
  }

  @Override
  public List<Account> getAccounts() {
    SQLiteDatabase db = dbManager.openDatabase();
    try {
      return AccountORM.list(db);
    } finally {
      dbManager.closeDatabase();
    }
  }

  @Override
  public boolean switchActiveAccount(Account account) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    Editor e = prefs.edit();

    e.putString(Constants.KEY_ACTIVE_ACCOUNT_ID, account.getId());
    e.commit();

    return true;
  }

  @Override
  public boolean needToRefreshAccessToken() {
    int FIVE_MINUTES = 300000;
    synchronized (this) {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      long tokenExpiresAt = prefs.getLong(Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT, -1);
      return System.currentTimeMillis() >= tokenExpiresAt - FIVE_MINUTES;
    }
  }

  @Override
  public void refreshAccessToken() {
    synchronized (this) {
      Log.i("Coinbase", "Refreshing access token...");

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
      String refreshToken = prefs.getString(Constants.KEY_ACCOUNT_REFRESH_TOKEN, null);

      List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
      parametersBody.add(new BasicNameValuePair("grant_type", "refresh_token"));
      parametersBody.add(new BasicNameValuePair("refresh_token", refreshToken));

      Object[] newTokens;

      try {
        newTokens = doTokenRequest(parametersBody);
      } catch(Exception e) {
        e.printStackTrace();
        Log.e("Coinbase", "Could not fetch new access token!");
        return;
      }

      if(newTokens == null) {
        // Authentication error.
        Log.e("Coinbase", "Authentication error when fetching new access token.");
        return;
      }

      Editor e = prefs.edit();

      e.putString(Constants.KEY_ACCOUNT_ACCESS_TOKEN, (String)newTokens[0]);
      e.putString(Constants.KEY_ACCOUNT_REFRESH_TOKEN, (String)newTokens[1]);
      e.putLong(Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT, System.currentTimeMillis() + 7200000);

      e.commit();
    }
  }

  // start three legged oauth handshake
  @Override
  public String generateOAuthUrl(String redirectUrl){
    String baseUrl = CLIENT_BASEURL + "/oauth/authorize";
    String device = Build.MODEL.startsWith(Build.MANUFACTURER) ? Build.MODEL : Build.MANUFACTURER + " " + Build.MODEL;

    try{
      redirectUrl = URLEncoder.encode(redirectUrl, "utf-8");
      device = URLEncoder.encode(device, "utf-8");
    } catch(Exception e) {
      throw new RuntimeException(e);
    }

    String scope = BuildConfig.type == BuildType.MERCHANT ? "merchant" : "all";

    String authorizeUrl = baseUrl + "?response_type=code&client_id=" + CLIENT_ID + "&redirect_uri=" + redirectUrl + "&scope=" + scope + "&meta[name]=" + device;
    return authorizeUrl;
  }

  // end three legged oauth handshake. (code to tokens)
  // Returns error as human-readable string, or null on success.
  @Override
  public String signin(Context context, String code, String originalRedirectUrl) {

    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("grant_type", "authorization_code"));
    parametersBody.add(new BasicNameValuePair("redirect_uri", originalRedirectUrl));
    parametersBody.add(new BasicNameValuePair("code", code));

    try {
      Object[] tokens = doTokenRequest(parametersBody);

      if(tokens == null) {
        return context.getString(R.string.login_error_auth);
      }

      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
      Editor e = prefs.edit();

      e.putString(Constants.KEY_ACCOUNT_ACCESS_TOKEN, (String)tokens[0]);
      e.putString(Constants.KEY_ACCOUNT_REFRESH_TOKEN, (String)tokens[1]);
      e.putLong(Constants.KEY_ACCOUNT_TOKEN_EXPIRES_AT, System.currentTimeMillis() + 7200000);
      e.putBoolean(Constants.KEY_ACCOUNT_VALID, true);

      e.commit();

      User user = getClient().getUser();

      e.putString(Constants.KEY_USER_ID, user.getId());
      e.putString(Constants.KEY_ACCOUNT_EMAIL, user.getEmail());
      e.putString(Constants.KEY_ACCOUNT_NATIVE_CURRENCY, user.getNativeCurrency().getCurrencyCode());
      e.putString(Constants.KEY_ACCOUNT_FULL_NAME, user.getName());
      e.putString(Constants.KEY_ACCOUNT_TIME_ZONE, user.getTimeZone());
      e.putString(Constants.KEY_ACCOUNT_LIMIT_BUY, user.getBuyLimit().getAmount().toPlainString());
      e.putString(Constants.KEY_ACCOUNT_LIMIT_SELL, user.getSellLimit().getAmount().toPlainString());
      e.putString(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_BUY, user.getBuyLimit().getCurrencyUnit().getCurrencyCode());
      e.putString(Constants.KEY_ACCOUNT_LIMIT_CURRENCY_SELL, user.getSellLimit().getCurrencyUnit().getCurrencyCode());
      e.commit();

      if (BuildConfig.type != BuildType.MERCHANT) {

        List<Account> accounts = getClient().getAccounts().getAccounts();

        SQLiteDatabase db = dbManager.openDatabase();
        try {
          boolean foundPrimaryAccount = false;
          for (Account account : accounts) {
            if (account.isActive()) {
              AccountORM.insert(db, account);
              if (account.isPrimary()) {
                e.putString(Constants.KEY_ACTIVE_ACCOUNT_ID, account.getId());
                e.commit();
                foundPrimaryAccount = true;
              }
            }
          }

          if (!foundPrimaryAccount) {
            throw new Exception("Could not find primary account");
          }

        } finally {
          dbManager.closeDatabase();
        }
      }

      return null;
    } catch (Exception e) {
      e.printStackTrace();
      PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();
      return context.getString(R.string.login_error_io);
    }
  }

  private Object[] doTokenRequest(Collection<BasicNameValuePair> params) throws IOException, JSONException {
    DefaultHttpClient client = new DefaultHttpClient();

    String baseUrl = CLIENT_BASEURL + "/oauth/token";

    HttpPost oauthPost = new HttpPost(baseUrl);
    List<BasicNameValuePair> parametersBody = new ArrayList<BasicNameValuePair>();
    parametersBody.add(new BasicNameValuePair("client_id", CLIENT_ID));
    parametersBody.add(new BasicNameValuePair("client_secret", CLIENT_SECRET));
    parametersBody.addAll(params);
    oauthPost.setEntity(new UrlEncodedFormEntity(parametersBody, HTTP.UTF_8));

    HttpResponse response = client.execute(oauthPost);
    int code = response.getStatusLine().getStatusCode();

    if(code == 401) {
      Log.e("Coinbase", "Authentication error getting token");
      return null;
    } else if(code != 200) {
      throw new IOException("Got HTTP response code " + code);
    }

    JSONObject content = new JSONObject(new JSONTokener(EntityUtils.toString(response.getEntity())));

    String accessToken = content.getString("access_token");
    String refreshToken = content.getString("refresh_token");

    return new Object[] { accessToken, refreshToken };
  }

  @Override
  public String getSelectedAccountName() {
    SQLiteDatabase db = dbManager.openDatabase();
    try {
      Account account = AccountORM.find(db, getActiveAccountId());
      return account.getName();
    } finally {
      dbManager.closeDatabase();
    }
  }

  @Override
  public void setAccountValid(boolean status, String desc) {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    Editor e = prefs.edit();
    e.putBoolean(Constants.KEY_ACCOUNT_VALID, status);
    e.putString(Constants.KEY_ACCOUNT_VALID_DESC, desc);
    e.commit();
  }

  @Override
  public String getAccountValid() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    if (prefs.getBoolean(Constants.KEY_ACCOUNT_VALID, false)) {
      return null; // Account valid
    } else {
      return prefs.getString(Constants.KEY_ACCOUNT_VALID_DESC, "No msg");
    }
  }

  @Override
  public String getActiveUserId() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getString(Constants.KEY_USER_ID, null);
  }

  @Override
  public String getReceiveAddress() {
    SQLiteDatabase db = dbManager.openDatabase();
    try {
      return AccountORM.getCachedReceiveAddress(db, getActiveAccountId());
    } finally {
      dbManager.closeDatabase();
    }
  }

  @Override
  public String getActiveAccountId() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getString(Constants.KEY_ACTIVE_ACCOUNT_ID, null);
  }

  @Override
  public String getActiveUserEmail() {
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getString(Constants.KEY_ACCOUNT_EMAIL, null);
  }

  private synchronized String getAccessToken() {
    if (needToRefreshAccessToken()) {
      refreshAccessToken();
    }

    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mContext);
    return prefs.getString(Constants.KEY_ACCOUNT_ACCESS_TOKEN, null);
  }

  @Override
  public void signout() {
    mContext.deleteDatabase(ClientCacheDatabase.DATABASE_NAME);
    SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    defaultPreferences.edit().clear().commit();
  }

  @Override
  public Coinbase getClient(String accountId) {
    return (Coinbase) Proxy.newProxyInstance(
            Coinbase.class.getClassLoader(),
            new Class [] {Coinbase.class},
            getCoinbaseHandler(accountId)
    );
  }

  // Proxy all requests to coinbase to invalidate tokens on 401
  @Override
  public Coinbase getClient() {
    return getClient(getActiveAccountId());
  }

  private class CoinbaseHandler implements InvocationHandler {
    private Coinbase underlying;

    public CoinbaseHandler(Coinbase underlying) {
      this.underlying = underlying;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] args) throws Throwable {
      Object result;
      try {
        result = method.invoke(underlying, args);
      } catch (InvocationTargetException ex) {
        if (ex.getTargetException() instanceof UnauthorizedException) {
          setAccountValid(false, "401 error");
        }
        throw ex.getTargetException();
      }
      return result;
    }
  }

  private InvocationHandler getCoinbaseHandler(String accountId) {
    Coinbase underlying =
          new CoinbaseBuilder()
            .withAccessToken(getAccessToken())
            .withAccountId(accountId)
            .build();

    return new CoinbaseHandler(underlying);
  }
}
