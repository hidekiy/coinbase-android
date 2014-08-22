package com.coinbase.android.test;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.test.ActivityInstrumentationTestCase2;

import com.coinbase.android.Constants;
import com.coinbase.android.TestCaseEntryPointActivity;
import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.ClientCacheDatabase;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.event.BusModule;
import com.coinbase.android.settings.PreferencesManager;
import com.coinbase.api.Coinbase;
import com.coinbase.api.LoginManager;
import com.google.inject.AbstractModule;
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.robotium.solo.Solo;
import com.squareup.otto.Bus;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import roboguice.RoboGuice;
import roboguice.config.DefaultRoboModule;

import static com.coinbase.android.test.MockResponses.mockAccount;
import static com.coinbase.android.test.MockResponses.mockCurrentUser;
import static com.coinbase.android.test.MockResponses.mockUser;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

public abstract class MockApiTest extends ActivityInstrumentationTestCase2 {
  private Solo solo;
  protected Coinbase mockCoinbase;
  protected LoginManager mockLoginManager;
  protected PreferencesManager mockPreferences;
  protected Bus testBus;
  protected DatabaseManager dbManager;

  /**
   * Real test activity.
   */
  protected Class<? extends Activity> testActivityClass;

  public class MockPreferencesModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(PreferencesManager.class).toInstance(mockPreferences);
    }
  }

  public class MockLoginManagerModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(LoginManager.class).toInstance(mockLoginManager);
    }
  }

  public class TestDbManagerModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(DatabaseManager.class).toInstance(dbManager);
    }
  }

  public class TestBusModule implements Module {
    @Override
    public void configure(Binder binder) {
      binder.bind(Bus.class).toInstance(testBus);
    }
  }

  public MockApiTest(final Class<? extends Activity> testActivityClass) {
    // - Setting an empty activity as test entry point.
    super(TestCaseEntryPointActivity.class);

    // - Real test activity.
    this.testActivityClass = testActivityClass;
  }

  public void setUp() throws Exception {
    super.setUp();

    System.setProperty("dexmaker.dexcache", getInstrumentation().getTargetContext().getCacheDir().getPath());

    mockCoinbase = mock(Coinbase.class);
    mockLoginManager = mock(LoginManager.class);
    mockPreferences = mock(PreferencesManager.class);
    testBus = new Bus();

    doReturn(mockCoinbase).when(mockLoginManager).getClient();
    doReturn(true).when(mockLoginManager).isSignedIn();
    doReturn(mockCurrentUser().getId()).when(mockLoginManager).getActiveUserId();
    doReturn(mockAccount().getId()).when(mockLoginManager).getActiveAccountId();
    doReturn(CurrencyUnit.USD).when(mockPreferences).getNativeCurrency();

    Application application =
            (Application) getInstrumentation().getTargetContext().getApplicationContext();
    application.deleteDatabase(ClientCacheDatabase.DATABASE_NAME);
    dbManager = new DatabaseManager(application);

    SQLiteDatabase db = dbManager.openDatabase();
    AccountORM.insert(db, mockAccount());
    dbManager.closeDatabase();

    Module roboGuiceModule = RoboGuice.newDefaultRoboModule(application);
    Module testModules = Modules.combine(
            new TestBusModule(),
            new MockLoginManagerModule(),
            new TestDbManagerModule(),
            new MockPreferencesModule()
    );
    Module testModule = Modules.override(roboGuiceModule).with(testModules);
    RoboGuice.setBaseApplicationInjector(application, RoboGuice.DEFAULT_STAGE, testModule);

    // Clear preferences
    SharedPreferences defaultPreferences = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext());
    defaultPreferences.edit().clear().commit();

    // - Initialize Robotium driver.
    solo = new Solo(getInstrumentation(), getActivity());
  }

  public void tearDown() throws Exception {
    ignoreSafeApiCalls();
    verifyNoMoreInteractions(mockCoinbase);
    solo.finishOpenedActivities();
    RoboGuice.util.reset();
    super.tearDown();
  }

  protected void startTestActivity() {
    final Intent intent = new Intent(getActivity(), testActivityClass);
    solo.getCurrentActivity().startActivity(intent);
    solo.waitForActivity(testActivityClass);
  }

  // We use strict verification to ensure no extraneous unsafe calls are made (purchases/transfers)
  // but we can ignore safe api calls
  protected void ignoreSafeApiCalls() throws Exception {
    verify(mockCoinbase, atLeast(0)).getContacts();
    verify(mockCoinbase, atLeast(0)).getContacts(anyString());
    verify(mockCoinbase, atLeast(0)).getExchangeRates();
    verify(mockCoinbase, atLeast(0)).getSupportedCurrencies();
    verify(mockCoinbase, atLeast(0)).getAddresses();
    verify(mockCoinbase, atLeast(0)).getUser();
    verify(mockCoinbase, atLeast(0)).getSellQuote(any(Money.class));
    verify(mockCoinbase, atLeast(0)).getBuyQuote(any(Money.class));
    verify(mockCoinbase, atLeast(0)).getTransactions();
    verify(mockCoinbase, atLeast(0)).getAccountChanges();
    verify(mockCoinbase, atLeast(0)).getAccountChanges(anyInt());
    verify(mockCoinbase, atLeast(0)).getTransaction(anyString());
    verify(mockCoinbase, atLeast(0)).getOrder(anyString());
  }

  protected Solo getSolo() {
    return solo;
  }

}
