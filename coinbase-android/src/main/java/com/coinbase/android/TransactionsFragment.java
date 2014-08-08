package com.coinbase.android;

import android.animation.LayoutTransition;
import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

import com.coinbase.android.db.AccountChangeORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.task.ApiTask;
import com.coinbase.android.util.InsertedItemListAdapter;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.AccountChange;
import com.coinbase.api.entity.AccountChangesResponse;
import com.google.inject.Inject;

import org.acra.ACRA;
import org.joda.money.CurrencyUnit;
import org.joda.money.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import roboguice.fragment.RoboListFragment;
import roboguice.inject.InjectResource;
import roboguice.util.RoboAsyncTask;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.AbsDefaultHeaderTransformer;
import uk.co.senab.actionbarpulltorefresh.extras.actionbarsherlock.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.Options;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

public class TransactionsFragment extends RoboListFragment implements CoinbaseFragment {

  private class SyncTransactionsTask extends ApiTask<Void> {

    public static final int MAX_ENDLESS_PAGES = 10;

    @Inject
    private DatabaseManager mDbManager;
    private Integer mStartPage;

    public SyncTransactionsTask(Context context, Integer startPage) {
      super(context);
      mStartPage = startPage;
    }

    @Override
    public Void call() throws Exception {
      String currentUserId = null;
      String activeAccount = mLoginManager.getActiveAccountId();
      int startPage = (mStartPage == null) ? 0 : mStartPage;
      int numPages = 1; // Real value will be set after first list iteration
      int loadedPage = startPage;

      AccountChangesResponse response = getClient().getAccountChanges(loadedPage);

      // Update balance
      // (we do it here to update the balance ASAP.)
      final Money btcBalance = response.getBalance();
      final Money nativeBalance = response.getNativeBalance();
      mParent.runOnUiThread(new Runnable() {
        public void run() {
          mBalanceBtc = btcBalance;
          mBalanceNative = nativeBalance;
          updateBalance();
        }
      });

      currentUserId = response.getCurrentUser().getId();

      List<AccountChange> accountChanges = response.getAccountChanges();
      numPages = response.getNumPages();
      loadedPage++;

      mMaxPage = numPages;

      /* TODO Also fetch extra info from /transactions call for first ~30 transactions
      List<Transaction> transactions = getClient().getTransactions().getTransactions();
      HashMap<String, Transaction> transactionsMap = new HashMap<String, Transaction>();
      if (transactions != null) {
        for (Transaction transaction : transactions) {
          transactionsMap.put(transaction.getId(), transaction);
        }
      }
      */

      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        db.beginTransaction();

        if(startPage == 0) {
          AccountChangeORM.clear(db, activeAccount);
        }

        for(AccountChange accountChange : accountChanges) {
          AccountChangeORM.insert(db, activeAccount, accountChange);
        }

        // TODO insert transactions to cache

        db.setTransactionSuccessful();
        mLastLoadedPage = loadedPage;

      } finally {
        db.endTransaction();
        mDbManager.closeDatabase();
      }

      return null;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void updateWidgets() {
      if(PlatformUtils.hasHoneycomb()) {
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(mParent);
        widgetManager.notifyAppWidgetViewDataChanged(
          widgetManager.getAppWidgetIds(new ComponentName(mParent, TransactionsAppWidgetProvider.class)),
          R.id.widget_list);
      }
    }

    @Override
    protected void onPreExecute() {
      // TODO mParent.setRefreshButtonAnimated(true);
      mBalanceBtc = mBalanceNative = null;

      if(mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.GONE);
      }
    }

    @Override
    public void onSuccess(Void v) {
      // Update list
      loadTransactionsList();

      // Update transaction widgets
      updateWidgets();

      // TODO Update the buy / sell history list
      // mParent.getBuySellFragment().onTransactionsSynced();

      // TODO Successful sync. This is a good time to check for any left over delayed TX.
      // mParent.startService(new Intent(mParent, DelayedTxSenderService.class));
    }

    @Override
    protected void onException(Exception ex) {
      if (mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.VISIBLE);

        // If we're disconnected from the internet, a sync error is expected, so
        // don't show an alarming red error message
        if (Utils.isConnectedOrConnecting(mParent)) {
          // Problem
          mSyncErrorView.setText(R.string.transactions_refresh_error);
          mSyncErrorView.setBackgroundColor(mParent.getResources().getColor(R.color.transactions_sync_error_critical));
        } else {
          // Internet is just disconnected
          mSyncErrorView.setText(R.string.transactions_internet_error);
          mSyncErrorView.setBackgroundColor(mParent.getResources().getColor(R.color.transactions_sync_error_calm));
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
        int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
        if (mLoginManager.getAccountValid(mParent, activeAccount) != null) {
          // Request failed because account is no longer valid
          if (getFragmentManager() != null) {
            new AccountInvalidDialogFragment().show(getFragmentManager(), "accountinvalid");
          }
        }
      }
      super.onException(ex);
    }

    @Override
    public void onFinally() {
      mSyncTask = null;
      // TODO mParent.setRefreshButtonAnimated(false);
    }

  }

  private interface TransactionDisplayItem {
    public void configureStatusView (TextView statusView);
    public void configureTitleView (TextView titleView);
    public void configureAmountView (TextView amountView);
    public void onClick();
  }

  private class AccountChangeDisplayItem implements TransactionDisplayItem {
    protected AccountChange mAccountChange;

    public AccountChangeDisplayItem(AccountChange accountChange) {
      mAccountChange = accountChange;
    }

    public void configureStatusView (TextView statusView) {
      String readable;
      int scolor;
      if (mAccountChange.isConfirmed()) {
        readable = getString(R.string.transaction_status_complete);
        scolor = R.color.transaction_inlinestatus_complete;
      } else {
        readable = getString(R.string.transaction_status_pending);
        scolor = R.color.transaction_inlinestatus_pending;
      }

      statusView.setText(readable);
      statusView.setTextColor(getResources().getColor(scolor));
      statusView.setTypeface(FontManager.getFont(mParent, "RobotoCondensed-Regular"));
    }

    public void configureTitleView (TextView titleView) {
      titleView.setText(Utils.generateAccountChangeSummary(mParent, mAccountChange));
      titleView.setTypeface(FontManager.getFont(mParent, "Roboto-Light"));
    }

    public void configureAmountView (TextView amountView) {
      Money amount = mAccountChange.getAmount();
      if (amount.isPositive()) {
        amountView.setTextColor(getResources().getColor(R.color.transaction_positive));
      } else if (amount.isNegative()) {
        amountView.setTextColor(getResources().getColor(R.color.transaction_negative));
      } else {
        amountView.setTextColor(getResources().getColor(R.color.transaction_neutral));
      }

      Money displayAmount = mAccountChange.getAmount().abs();
      amountView.setText(Utils.formatMoneyRounded(displayAmount));
    }

    @Override
    public void onClick() {
      if (mDetailsShowing) {
        return;
      }

      String transactionId = mAccountChange.getTransactionId();
      Bundle args = new Bundle();
      args.putString(TransactionDetailsFragment.ID, transactionId);
      TransactionDetailsFragment fragment = new TransactionDetailsFragment();
      fragment.setArguments(args);

      FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
      transaction.add(R.id.transaction_details_host, fragment);
      transaction.addToBackStack("details");
      transaction.commit();

      showDetails();
    }
  }

  private class TransactionsAdapter extends BaseAdapter {
    private LayoutInflater mInflater;
    private List<TransactionDisplayItem> mItems;

    public TransactionsAdapter(Context context, List<TransactionDisplayItem> items) {
      mInflater = LayoutInflater.from(context);
      mItems = items;
    }

    @Override
    public int getCount() {
      return mItems.size();
    }

    @Override
    public TransactionDisplayItem getItem(int position) {
      return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final TransactionDisplayItem item = getItem(position);
      View view = convertView;
      if (view == null) {
        view = mInflater.inflate(R.layout.fragment_transactions_item, null);
      }

      TextView titleView  = (TextView) view.findViewById(R.id.transaction_title);
      TextView amountView = (TextView) view.findViewById(R.id.transaction_amount);
      TextView statusView = (TextView) view.findViewById(R.id.transaction_status);

      item.configureAmountView(amountView);
      item.configureStatusView(statusView);
      item.configureTitleView(titleView);

      return view;
    }
  }

  private class LoadTransactionsTask extends RoboAsyncTask<List<TransactionDisplayItem>> {
    @Inject
    protected DatabaseManager mDbManager;

    @Inject
    protected LoginManager mLoginManager;

    public LoadTransactionsTask(Context context) {
      super(context);
    }

    @Override
    public List<TransactionDisplayItem> call() throws Exception {
      List<TransactionDisplayItem> items = new ArrayList<TransactionDisplayItem>();

      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        String activeAccount = mLoginManager.getActiveAccountId();
        List<AccountChange> accountChanges = AccountChangeORM.getOrderedAccountChanges(db, activeAccount);

        for (AccountChange accountChange : accountChanges) {
          items.add(new AccountChangeDisplayItem(accountChange));
        }

        return items;
      } finally {
        mDbManager.closeDatabase();
      }
    }

    @Override
    public void onSuccess(List<TransactionDisplayItem> items) {
      if (mListView != null) {

        setHeaderPinned(items.isEmpty());
        mListFooter.setVisibility(canLoadMorePages() ? View.VISIBLE : View.GONE);

        // "rate me" notice
        Constants.RateNoticeState rateNoticeState = Constants.RateNoticeState.valueOf(Utils.getPrefsString(mParent, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN.name()));
        boolean showRateNotice = rateNoticeState == Constants.RateNoticeState.SHOULD_SHOW_NOTICE;

        TransactionsAdapter adapter = new TransactionsAdapter(mParent, items);

        View rateNotice = getRateNotice();
        InsertedItemListAdapter wrappedAdapter = new InsertedItemListAdapter(adapter, rateNotice, 2);
        wrappedAdapter.setInsertedViewVisible(showRateNotice);

        mListView.setAdapter(wrappedAdapter);
      }
    }

    @Override
    public void onFinally() {
      refreshComplete();
    }
  }

  private class TransactionsInfiniteScrollListener implements OnScrollListener {

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem,
                         int visibleItemCount, int totalItemCount) {

      int padding = 2;
      boolean shouldLoadMore = firstVisibleItem + visibleItemCount + padding >= totalItemCount;

      if(shouldLoadMore && canLoadMorePages()) {

        // Load more transactions
        if(mSyncTask == null) {
          Log.i("Coinbase", "Infinite scroll is loading more pages (last loaded page " + mLastLoadedPage + ", max " + mMaxPage + ")");
          mSyncTask = new SyncTransactionsTask(view.getContext(), mLastLoadedPage);
          mSyncTask.execute();
        }
      }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
      // Unused
    }
  }

  Activity mParent;

  boolean mBalanceLoading, mAnimationPlaying;
  FrameLayout mListHeaderContainer;
  ListView mListView;
  ViewGroup mBaseView, mListHeader, mMainView;
  View mListFooter;
  View mRateNotice;
  TextView mBalanceText, mBalanceHome;
  TextView mSyncErrorView;
  PullToRefreshLayout mPullToRefreshLayout;
  boolean mDetailsShowing = false;
  Money mBalanceBtc, mBalanceNative;

  SyncTransactionsTask mSyncTask;
  int mLastLoadedPage = -1, mMaxPage = -1;

  @InjectResource(R.string.wallet_balance_home) String mNativeBalanceFormatString;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mParent = activity;
  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putBoolean("details_showing", mDetailsShowing);
  }

  private boolean canLoadMorePages() {
    return mLastLoadedPage != -1 && mLastLoadedPage < SyncTransactionsTask.MAX_ENDLESS_PAGES &&
            mLastLoadedPage < mMaxPage;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    // Inflate base layout
    mBaseView = (ViewGroup) inflater.inflate(R.layout.fragment_transactions, container, false);
    mMainView = (ViewGroup) mBaseView.findViewById(R.id.inner_view);

    mListView = (ListView) mBaseView.findViewById(android.R.id.list);

    // Inflate header (which contains account balance)
    mListHeader = (ViewGroup) inflater.inflate(R.layout.fragment_transactions_header, null, false);
    mListHeaderContainer = new FrameLayout(mParent);
    setHeaderPinned(true);
    mListView.addHeaderView(mListHeaderContainer);

    // Footer
    ViewGroup listFooterParent = (ViewGroup) inflater.inflate(R.layout.fragment_transactions_footer, null, false);
    mListFooter = listFooterParent.findViewById(R.id.transactions_footer_text);
    mListView.addFooterView(listFooterParent);

    // Header card swipe
    boolean showBalance = Utils.getPrefsBool(mParent, Constants.KEY_ACCOUNT_SHOW_BALANCE, true);
    mListHeader.findViewById(R.id.wallet_layout).setVisibility(showBalance ? View.VISIBLE : View.GONE);
    mListHeader.findViewById(R.id.wallet_hidden_notice).setVisibility(showBalance ? View.GONE : View.VISIBLE);
    final BalanceTouchListener balanceTouchListener = new BalanceTouchListener(mListHeader.findViewById(R.id.wallet_layout),
            null, new BalanceTouchListener.OnDismissCallback() {
      @Override
      public void onDismiss(View view, Object token) {

        // Hide balance
        mListHeader.findViewById(R.id.wallet_layout).setVisibility(View.GONE);
        mListHeader.findViewById(R.id.wallet_hidden_notice).setVisibility(View.VISIBLE);

        // Save in preferences
        PreferenceManager.getDefaultSharedPreferences(mParent).edit()
                .putBoolean(String.format(Constants.KEY_ACCOUNT_SHOW_BALANCE, Utils.getActiveAccount(mParent)), false)
                .commit();
      }
    });
    mListHeader.setOnTouchListener(balanceTouchListener);

    if (Build.VERSION.SDK_INT >= 11) {
      LayoutTransition transition = new LayoutTransition();
      //transition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
      mListHeader.setLayoutTransition(transition);
    }

    mListHeader.findViewById(R.id.wallet_hidden_show).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {

        // Show balance
        mListHeader.findViewById(R.id.wallet_layout).setVisibility(View.VISIBLE);
        mListHeader.findViewById(R.id.wallet_hidden_notice).setVisibility(View.GONE);
        balanceTouchListener.reset();

        // Save in preferences
        PreferenceManager.getDefaultSharedPreferences(mParent).edit()
                .putBoolean(String.format(Constants.KEY_ACCOUNT_SHOW_BALANCE, Utils.getActiveAccount(mParent)), true)
                .commit();
      }
    });

    mListView.setOnScrollListener(new TransactionsInfiniteScrollListener());

    mBalanceText = (TextView) mListHeader.findViewById(R.id.wallet_balance);
    mBalanceHome = (TextView) mListHeader.findViewById(R.id.wallet_balance_home);
    mSyncErrorView = (TextView) mListHeader.findViewById(R.id.wallet_error);

    ((TextView) mBaseView.findViewById(R.id.wallet_balance_label)).setTypeface(
            FontManager.getFont(mParent, "RobotoCondensed-Regular"));
    ((TextView) mBaseView.findViewById(R.id.wallet_send_label)).setTypeface(
           FontManager.getFont(mParent, "RobotoCondensed-Regular"));

    mBalanceText.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Utils.togglePrefsBool(mParent, Constants.KEY_ACCOUNT_BALANCE_FUZZY, true);
        setBalance((Money) v.getTag());
      }
    });

    // Load old balance
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
    int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);
    String oldBalance = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE, activeAccount), null);
    String oldHomeBalance = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME, activeAccount), null);
    String oldHomeCurrency = prefs.getString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME_CURRENCY, activeAccount), null);

    if(oldBalance != null) {
      try {
        setBalance(Money.of(CurrencyUnit.getInstance("BTC"), new BigDecimal(oldBalance)));
      } catch (NumberFormatException e) {
        // Old versions of the app would store the balance in a localized format
        // ex. in some countries with the decimal separator "," instead of "."
        // Restoring balance will fail on upgrade, so just ignore it
        // and reload the balance from the network
      }
      mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color));
      mBalanceHome.setText(String.format(mParent.getString(R.string.wallet_balance_home), oldHomeBalance));
    }

    if(mBalanceLoading) {
      mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));
    }

    mBaseView.findViewById(R.id.wallet_send).setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {
        // TODO open transfer menu
      }
    });

    // Load transaction list
    loadTransactionsList();

    if (savedInstanceState != null && savedInstanceState.getBoolean("details_showing", false)) {
      mDetailsShowing = true;
      mBaseView.findViewById(R.id.transaction_details_background).setVisibility(View.VISIBLE);
    }

    return mBaseView;
  }

  @Override
  public void onStart() {
    // Configure pull to refresh
    mPullToRefreshLayout = new PullToRefreshLayout(mParent);
    AbsDefaultHeaderTransformer ht =
            (AbsDefaultHeaderTransformer) new AbsDefaultHeaderTransformer();
    ActionBarPullToRefresh.from(mParent)
            .insertLayoutInto(mBaseView)
            .theseChildrenArePullable(android.R.id.list)
            .listener(new OnRefreshListener() {
              @Override
              public void onRefreshStarted(View view) {
                // TODO refresh things in other views too ?
                refresh();
              }
            })
            .options(Options.create().headerTransformer(ht).build())
            .setup(mPullToRefreshLayout);
    ht.setPullText("Swipe down to refresh");
    ht.setRefreshingText("Refreshing...");

    super.onStart();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    //mPullToRefreshAttacher.onConfigurationChanged(newConfig);
  }

  private void setBalance(Money balance) {
    boolean fuzzy = Utils.getPrefsBool(mParent, Constants.KEY_ACCOUNT_BALANCE_FUZZY, true);
    String balanceString;
    if (fuzzy) {
      balanceString = Utils.formatMoneyRounded(mBalanceBtc);
    } else {
      balanceString = Utils.formatMoney(mBalanceBtc);
    }
    mBalanceText.setText(balanceString);
    mBalanceText.setTag(balance);
  }

  private View getRateNotice() {

    if (mRateNotice != null) {
      return mRateNotice;
    }

    View rateNotice = View.inflate(mParent, R.layout.fragment_transactions_rate_notice, null);

    ((TextView) rateNotice.findViewById(R.id.rate_notice_title)).setTypeface(FontManager.getFont(mParent, "Roboto-Light"));

    TextView btnPositive = (TextView) rateNotice.findViewById(R.id.rate_notice_btn_positive),
            btnNegative = (TextView) rateNotice.findViewById(R.id.rate_notice_btn_negative);
    btnPositive.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // Permanently hide notice
        setRateNoticeState(Constants.RateNoticeState.NOTICE_DISMISSED, true);
        // Open Play Store
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.coinbase.android")));
      }
    });
    btnNegative.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // Permanently hide notice
        setRateNoticeState(Constants.RateNoticeState.NOTICE_DISMISSED, true);
      }
    });

    mRateNotice = rateNotice;
    return rateNotice;
  }

  public void setRateNoticeState(Constants.RateNoticeState state, boolean force) {

    Constants.RateNoticeState rateNoticeState = Constants.RateNoticeState.valueOf(Utils.getPrefsString(mParent, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN.name()));
    if (rateNoticeState == Constants.RateNoticeState.NOTICE_DISMISSED && !force) {
      return;
    }

    Utils.putPrefsString(mParent, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, state.name());
    if (getAdapter() != null) {
      getAdapter(InsertedItemListAdapter.class).setInsertedViewVisible(state == Constants.RateNoticeState.SHOULD_SHOW_NOTICE);
      getAdapter().notifyDataSetChanged();
    }
  }

  // Refresh just account balance.
  private void refreshBalance() {
    mBalanceLoading = true;
    mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));
    // TODO new LoadJustBalanceTask().execute();
  }

  private void updateBalance() {
    if (mBalanceBtc == null || mBalanceText == null) {
      return; // Not ready yet.
    }

    // Balance is loaded! update the view
    mBalanceLoading = false;

    try {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParent);
      int activeAccount = prefs.getInt(Constants.KEY_ACTIVE_ACCOUNT, -1);

      // Save balance in preferences
      Editor editor = prefs.edit();
      editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE, activeAccount), mBalanceBtc.getAmount().toPlainString());
      editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME, activeAccount), mBalanceNative.getAmount().toPlainString());
      editor.putString(String.format(Constants.KEY_ACCOUNT_BALANCE_HOME_CURRENCY, activeAccount), mBalanceNative.getCurrencyUnit().getCurrencyCode());
      editor.commit();

      // Update the view.
      mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color));
      setBalance(mBalanceBtc);
      mBalanceHome.setText(String.format(mNativeBalanceFormatString, Utils.formatMoney(mBalanceNative)));
    } catch (Exception e) {
      e.printStackTrace();
      ACRA.getErrorReporter().handleException(new RuntimeException("updateBalance()", e));
    }
  }

  public void refresh() {
    // Make balance appear invalidated
    mBalanceLoading = true;
    mBalanceText.setTextColor(mParent.getResources().getColor(R.color.wallet_balance_color_invalid));

    // Reload transactions + balance
    if(mSyncTask == null) {
      mSyncTask = new SyncTransactionsTask(mParent, null);
      mSyncTask.execute();
    }
  }

  public void refreshComplete() {
    mPullToRefreshLayout.setRefreshComplete();
  }

  private void setHeaderPinned(boolean pinned) {

    mMainView.removeView(mListHeader);
    mListHeaderContainer.removeAllViews();

    if(pinned) {
      mMainView.addView(mListHeader, 0);
      System.out.println("Main view has " + mMainView.getChildCount());
    } else {
      mListHeaderContainer.addView(mListHeader);
    }
  }

  // TODO
  /*
  public void insertTransactionAnimated(final int insertAtIndex, final JSONObject transaction, final String category, final String status) {
    if (!PlatformUtils.hasHoneycomb()) {
      // Do not play animation!
      try {
        Utils.insertTransaction(mParent, transaction, Utils.createAccountChangeForTransaction(mParent, transaction, category), status);
        loadTransactionsList();
      } catch (Exception e) {
        throw new RuntimeException("Malformed JSON from Coinbase", e);
      }
      refreshBalance();
      return;
    }

    mAnimationPlaying = true;
    getListView().setEnabled(false);
    setRateNoticeState(Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN, false);
    refreshBalance();
    getListView().post(new Runnable() {
      @Override
      public void run() {
        getListView().setSelection(0);
        getListView().postDelayed(new Runnable() {
          public void run() {
            _insertTransactionAnimated(insertAtIndex, transaction, category, status);
          }
        }, 500);
      }
    });
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void _insertTransactionAnimated(int insertAtIndex, JSONObject transaction, String category, String status) {

    // Step 1
    // Take a screenshot of the relevant part of the list view and put it over top of the real one
    Bitmap bitmap;
    final FrameLayout root = (FrameLayout) getView().findViewById(R.id.root);
    int height = 0, heightToCropOff = 0;
    boolean animateListView = true;
    if (mListHeaderContainer.getChildCount() > 0) { // Header not pinned
      bitmap = Bitmap.createBitmap(getListView().getWidth(), getListView().getHeight(), Bitmap.Config.ARGB_8888);
      Canvas canvas = new Canvas(bitmap);
      getListView().draw(canvas);
      for (int i = 0; i <= insertAtIndex; i++) {
        heightToCropOff += getListView().getChildAt(i).getHeight();
      }
      height = getListView().getHeight() - heightToCropOff;
    } else { // Header pinned
      bitmap = null; // No list view animation is needed
      animateListView = false;
      heightToCropOff = mListHeader.getHeight();
      height = root.getHeight() - heightToCropOff;
    }

    DisplayMetrics metrics = getResources().getDisplayMetrics();
    final ImageView fakeListView = new ImageView(mParent);
    fakeListView.setImageBitmap(bitmap);

    Matrix m = new Matrix();
    m.setTranslate(0, -heightToCropOff);
    fakeListView.setImageMatrix(m);
    fakeListView.setScaleType(ImageView.ScaleType.MATRIX);

    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
    params.topMargin = heightToCropOff + getListView().getDividerHeight();
    fakeListView.setLayoutParams(params);

    // Step 2
    // Create a fake version of the new list item and a background for it to animate onto
    JSONObject accountChange;
    View newListItem;
    try {
      accountChange = Utils.createAccountChangeForTransaction(mParent, transaction, category);

      newListItem = View.inflate(mParent, R.layout.fragment_transactions_item, null);
      TransactionViewBinder binder = new TransactionViewBinder();
      for (int i : new int[] { R.id.transaction_title, R.id.transaction_amount,
              R.id.transaction_currency }) {
        if (newListItem.findViewById(i) != null) {
          binder.setViewValue(newListItem.findViewById(i), accountChange.toString());
        }
      }
      binder.setViewValue(newListItem.findViewById(R.id.transaction_status), status);
      newListItem.setBackgroundColor(Color.WHITE);
    } catch (JSONException e) {
      throw new RuntimeException("Malformed JSON from Coinbase", e);
    }
    int itemHeight = (int)(70 * metrics.density);
    FrameLayout.LayoutParams itemParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, itemHeight);
    itemParams.topMargin = heightToCropOff + getListView().getDividerHeight(); // account for divider
    newListItem.setLayoutParams(itemParams);

    final View background = new View(mParent);
    background.setBackgroundColor(animateListView ? Color.parseColor("#eeeeee") : Color.WHITE);
    FrameLayout.LayoutParams bgParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
    bgParams.topMargin = heightToCropOff;
    background.setLayoutParams(bgParams);

    root.addView(background, root.getChildCount());
    if (animateListView) {
      root.addView(fakeListView, root.getChildCount());
    }
    root.addView(newListItem, root.getChildCount());

    // Step 3
    // Animate
    AnimatorSet set = new AnimatorSet();

    newListItem.setTranslationX(-metrics.widthPixels);
    ObjectAnimator itemAnimation = ObjectAnimator.ofFloat(newListItem, "translationX", -metrics.widthPixels, 0);
    ObjectAnimator listAnimation = ObjectAnimator.ofFloat(fakeListView, "translationY", 0, itemHeight);

    if (animateListView) {
      set.playSequentially(listAnimation, itemAnimation);
    } else {
      set.play(itemAnimation);
    }
    set.setDuration(300);
    final View _newListItem = newListItem;
    set.addListener(new Animator.AnimatorListener() {
      @Override
      public void onAnimationStart(Animator animation) {

      }

      @Override
      public void onAnimationEnd(Animator animation) {
        mAnimationPlaying = false;
        root.removeView(_newListItem);
        root.removeView(fakeListView);
        root.removeView(background);
        getListView().setEnabled(true);
      }

      @Override
      public void onAnimationCancel(Animator animation) {

      }

      @Override
      public void onAnimationRepeat(Animator animation) {

      }
    });
    set.start();

    // Step 4
    // Now that the animation is started, update the actual list values behind-the-scenes
    Utils.insertTransaction(mParent, transaction, accountChange, status);
    loadTransactionsList();
  }

  */

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public void loadTransactionsList() {
    new LoadTransactionsTask(mParent).execute();
  }

  private TransactionsAdapter getAdapter() {
    return getAdapter(TransactionsAdapter.class);
  }

  private <T> T getAdapter(Class<T> adapterType) {
    Adapter adapter = mListView.getAdapter();
    while (adapter instanceof WrapperListAdapter && !adapterType.equals(adapter.getClass())) {
      adapter = ((WrapperListAdapter) adapter).getWrappedAdapter(); // Un-wrap adapter
    }
    return (T) adapter;
  }

  @Override
  public void onListItemClick(ListView l, View v, int position, long id) {
    TransactionDisplayItem i = (TransactionDisplayItem) l.getItemAtPosition(position);
    i.onClick();
  }

  private void showDetails() {
    mDetailsShowing = true;

    // 1. animate
    getView().findViewById(R.id.transaction_details_background).setVisibility(View.VISIBLE);
    getView().findViewById(R.id.transaction_details_background).startAnimation(
            AnimationUtils.loadAnimation(mParent, R.anim.transactiondetails_bg_enter));
    getView().findViewById(R.id.transaction_details_host).startAnimation(
            AnimationUtils.loadAnimation(mParent, R.anim.transactiondetails_enter));

    // 2. if necessary, change action bar
    // TODO mParent.setInTransactionDetailsMode(true);

    // 3. pull to refresh
    mPullToRefreshLayout.setEnabled(false);
  }

  protected void hideDetails(boolean animated) {
    mDetailsShowing = false;

    if(animated) {
      Animation bg = AnimationUtils.loadAnimation(mParent, R.anim.transactiondetails_bg_exit);
      bg.setAnimationListener(new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {

        }

        @Override
        public void onAnimationEnd(Animation animation) {
          getView().findViewById(R.id.transaction_details_background).setVisibility(View.GONE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
      });
      getView().findViewById(R.id.transaction_details_background).startAnimation(bg);
    } else {
      getView().findViewById(R.id.transaction_details_background).setVisibility(View.GONE);
    }

    FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
    transaction.setCustomAnimations(0, animated ? R.anim.transactiondetails_exit : 0);
    transaction.remove(getChildFragmentManager().findFragmentById(R.id.transaction_details_host));
    transaction.commit();

    // 2. action bar
    // TODO mParent.setInTransactionDetailsMode(false);

    // 3. pull to refresh
    mPullToRefreshLayout.setEnabled(true);
  }

  public boolean onBackPressed() {
    if(mDetailsShowing) {
      hideDetails(true);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void onSwitchedTo() {
    int appUsageCount = Utils.getPrefsInt(mParent, Constants.KEY_ACCOUNT_APP_USAGE, 0);
    if (appUsageCount >= 2 && !mAnimationPlaying) {
      setRateNoticeState(Constants.RateNoticeState.SHOULD_SHOW_NOTICE, false);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    // TODO bind to transaction polling service here
    refresh();
    loadTransactionsList();
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    if (mDetailsShowing) {
      // TODO
      // ((TransactionDetailsFragment ) getChildFragmentManager().findFragmentById(R.id.transaction_details_host)).onPINPromptSuccessfulReturn();
    } else {
      // Not used
    }
  }

  @Override
  public String getTitle() {
    return getString(R.string.title_transactions);
  }
}
