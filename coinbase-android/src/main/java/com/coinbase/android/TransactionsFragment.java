package com.coinbase.android;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.util.DisplayMetrics;
import com.bugsnag.android.Bugsnag;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

import com.coinbase.android.db.AccountChangeORM;
import com.coinbase.android.db.AccountORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.db.DelayedTransactionORM;
import com.coinbase.android.db.TransactionORM;
import com.coinbase.android.event.BuySellMadeEvent;
import com.coinbase.android.event.NewDelayedTransactionEvent;
import com.coinbase.android.event.RefreshRequestedEvent;
import com.coinbase.android.event.TransactionsSyncedEvent;
import com.coinbase.android.event.TransferMadeEvent;
import com.coinbase.android.task.ApiTask;
import com.coinbase.android.util.InsertedItemListAdapter;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.AccountChange;
import com.coinbase.api.entity.AccountChangesResponse;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import org.joda.money.Money;
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

  public static interface Listener {
    public void onSendMoneyClicked();
    public void onStartTransactionsSync();
    public void onFinishTransactionsSync();
    public void onEnteringDetailsMode();
    public void onExitingDetailsMode();
  }

  private class SyncTransactionsTask extends ApiTask<Void> {

    public static final int MAX_ENDLESS_PAGES = 10;

    @Inject
    private DatabaseManager mDbManager;

    @Inject
    private Bus mBus;

    private Integer mStartPage;

    public SyncTransactionsTask(Context context, Integer startPage) {
      super(context);
      mStartPage = startPage;
    }

    @Override
    public Void call() throws Exception {
      String activeAccount = mLoginManager.getActiveAccountId();
      int startPage = (mStartPage == null) ? 0 : mStartPage;
      int numPages = 1; // Real value will be set after first list iteration
      int loadedPage = startPage;

      AccountChangesResponse response = getClient().getAccountChanges(loadedPage + 1);

      // Update balance
      // (we do it here to update the balance ASAP.)
      final Money btcBalance = response.getBalance();
      final Money nativeBalance = response.getNativeBalance();
      mParentActivity.runOnUiThread(new Runnable() {
        public void run() {
          mBalanceBtc = btcBalance;
          mBalanceNative = nativeBalance;
          updateBalance();
        }
      });

      List<AccountChange> accountChanges = response.getAccountChanges();
      numPages = response.getNumPages();
      loadedPage++;

      mMaxPage = numPages;

      List<Transaction> transactions = getClient().getTransactions().getTransactions();

      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        db.beginTransaction();

        if(startPage == 0) {
          AccountChangeORM.clear(db, activeAccount);
        }

        for(AccountChange accountChange : accountChanges) {
          AccountChangeORM.insert(db, activeAccount, accountChange);
        }

        if (transactions != null) {
          for (Transaction transaction : transactions) {
            TransactionORM.insertOrUpdate(db, activeAccount, transaction);
          }
        }

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
        AppWidgetManager widgetManager = AppWidgetManager.getInstance(mParentActivity);
        widgetManager.notifyAppWidgetViewDataChanged(
          widgetManager.getAppWidgetIds(new ComponentName(mParentActivity, TransactionsAppWidgetProvider.class)),
          R.id.widget_list);
      }
    }

    @Override
    protected void onPreExecute() {
      mListener.onStartTransactionsSync();
      mBalanceBtc = mBalanceNative = null;

      if(mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.GONE);
      }
    }

    @Override
    protected void onException(Exception ex) {
      if (mSyncErrorView != null) {
        mSyncErrorView.setVisibility(View.VISIBLE);

        // If we're disconnected from the internet, a sync error is expected, so
        // don't show an alarming red error message
        if (Utils.isConnectedOrConnecting(mParentActivity)) {
          // Problem
          mSyncErrorView.setText(R.string.transactions_refresh_error);
          mSyncErrorView.setBackgroundColor(mParentActivity.getResources().getColor(R.color.transactions_sync_error_critical));
        } else {
          // Internet is just disconnected
          mSyncErrorView.setText(R.string.transactions_internet_error);
          mSyncErrorView.setBackgroundColor(mParentActivity.getResources().getColor(R.color.transactions_sync_error_calm));
        }

        if (!mLoginManager.isSignedIn()) {
          mLoginManager.signout();
          getActivity().finish();
        }
      }
      super.onException(ex);
    }

    @Override
    public void onFinally() {
      // Update list
      loadTransactionsList();

      // Update transaction widgets
      updateWidgets();

      mBus.post(new TransactionsSyncedEvent());

      mSyncTask = null;
      mListener.onFinishTransactionsSync();
    }
  }

  private interface TransactionListDisplayItem {
    public void configureStatusView (TextView statusView);
    public void configureTitleView (TextView titleView);
    public void configureAmountView (TextView amountView);
    public void onClick();
  }

  private class TransactionDisplayItem implements TransactionListDisplayItem {
    protected Transaction mTransaction;

    public TransactionDisplayItem(Transaction transaction) {
      mTransaction = transaction;
    }

    public void configureStatusView (TextView statusView) {
      String readable;
      int scolor;

      switch (mTransaction.getStatus()) {
        case COMPLETE:
          readable = getString(R.string.transaction_status_complete);
          scolor = R.color.transaction_inlinestatus_complete;
          break;
        default:
          readable = getString(R.string.transaction_status_pending);
          scolor = R.color.transaction_inlinestatus_pending;
          break;
      }

      statusView.setText(readable);
      statusView.setTextColor(getResources().getColor(scolor));
      statusView.setTypeface(FontManager.getFont(mParentActivity, "RobotoCondensed-Regular"));
    }

    public void configureTitleView (TextView titleView) {
      titleView.setText(Utils.generateTransactionSummary(mParentActivity, mTransaction));
      titleView.setTypeface(FontManager.getFont(mParentActivity, "Roboto-Light"));
    }

    public void configureAmountView (TextView amountView) {
      Money amount = mTransaction.getAmount();
      if (amount.isPositive()) {
        amountView.setTextColor(getResources().getColor(R.color.transaction_positive));
      } else if (amount.isNegative()) {
        amountView.setTextColor(getResources().getColor(R.color.transaction_negative));
      } else {
        amountView.setTextColor(getResources().getColor(R.color.transaction_neutral));
      }

      Money displayAmount = mTransaction.getAmount().abs();
      amountView.setText(Utils.formatMoneyRounded(displayAmount));
    }

    @Override
    public void onClick() {
      if (mDetailsShowing) {
        return;
      }

      String transactionId = mTransaction.getId();
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

  private class AccountChangeDisplayItem implements TransactionListDisplayItem {
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
      statusView.setTypeface(FontManager.getFont(mParentActivity, "RobotoCondensed-Regular"));
    }

    public void configureTitleView (TextView titleView) {
      titleView.setText(Utils.generateAccountChangeSummary(mParentActivity, mAccountChange));
      titleView.setTypeface(FontManager.getFont(mParentActivity, "Roboto-Light"));
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

  private class DelayedTransactionDisplayItem implements TransactionListDisplayItem {
    protected Transaction mTransaction;

    public DelayedTransactionDisplayItem(Transaction transaction) {
      mTransaction = transaction;
    }

    @Override
    public void configureStatusView(TextView statusView) {
      statusView.setText(getString(R.string.transaction_status_delayed));
      statusView.setTextColor(getResources().getColor(R.color.transaction_inlinestatus_delayed));
      statusView.setTypeface(FontManager.getFont(mParentActivity, "RobotoCondensed-Regular"));
    }

    @Override
    public void configureTitleView(TextView titleView) {
      titleView.setText(Utils.generateDelayedTransactionSummary(mParentActivity, mTransaction));
      titleView.setTypeface(FontManager.getFont(mParentActivity, "Roboto-Light"));
    }

    @Override
    public void configureAmountView(TextView amountView) {
      if (mTransaction.isRequest()) {
        amountView.setTextColor(getResources().getColor(R.color.transaction_positive));
      } else {
        amountView.setTextColor(getResources().getColor(R.color.transaction_negative));
      }

      Money displayAmount = mTransaction.getAmount().abs();
      amountView.setText(Utils.formatMoneyRounded(displayAmount));
    }

    @Override
    public void onClick() {
      if (mDetailsShowing) {
        return;
      }

      String transactionId = mTransaction.getId();
      Bundle args = new Bundle();
      args.putString(TransactionDetailsFragment.ID, transactionId);
      args.putBoolean(TransactionDetailsFragment.DELAYED, true);
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
    private List<TransactionListDisplayItem> mItems;

    public TransactionsAdapter(Context context, List<TransactionListDisplayItem> items) {
      mInflater = LayoutInflater.from(context);
      mItems = items;
    }

    @Override
    public int getCount() {
      return mItems.size();
    }

    @Override
    public TransactionListDisplayItem getItem(int position) {
      return mItems.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      final TransactionListDisplayItem item = getItem(position);
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

  private class LoadTransactionsTask extends RoboAsyncTask<List<TransactionListDisplayItem>> {
    @Inject
    protected DatabaseManager mDbManager;

    @Inject
    protected LoginManager mLoginManager;

    public LoadTransactionsTask(Context context) {
      super(context);
    }

    @Override
    public List<TransactionListDisplayItem> call() throws Exception {
      List<TransactionListDisplayItem> items = new ArrayList<TransactionListDisplayItem>();

      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        String activeAccount = mLoginManager.getActiveAccountId();

        List<Transaction> delayedTransactions = DelayedTransactionORM.getTransactions(db, activeAccount);
        for (Transaction tx : delayedTransactions) {
          items.add(new DelayedTransactionDisplayItem(tx));
        }

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
    public void onSuccess(List<TransactionListDisplayItem> items) {
      mItems = items;

      if (mListView != null) {

        setHeaderPinned(items.isEmpty());
        mListFooter.setVisibility(canLoadMorePages() ? View.VISIBLE : View.GONE);

        // "rate me" notice
        Constants.RateNoticeState rateNoticeState = Constants.RateNoticeState.valueOf(Utils.getPrefsString(mParentActivity, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN.name()));
        boolean showRateNotice = rateNoticeState == Constants.RateNoticeState.SHOULD_SHOW_NOTICE;

        TransactionsAdapter adapter = new TransactionsAdapter(mParentActivity, mItems);

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

  Activity mParentActivity;
  Listener mListener;

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

  List<TransactionListDisplayItem> mItems;
  SyncTransactionsTask mSyncTask;
  int mLastLoadedPage = -1, mMaxPage = -1;

  @InjectResource(R.string.wallet_balance_home) String mNativeBalanceFormatString;

  @Inject
  LoginManager mLoginManager;

  @Inject
  DatabaseManager mDbManager;

  @Inject
  Bus mBus;

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    mParentActivity = activity;
    mListener = (Listener) mParentActivity;
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
    mListHeaderContainer = new FrameLayout(mParentActivity);
    setHeaderPinned(true);
    mListView.addHeaderView(mListHeaderContainer);

    // Footer
    ViewGroup listFooterParent = (ViewGroup) inflater.inflate(R.layout.fragment_transactions_footer, null, false);
    mListFooter = listFooterParent.findViewById(R.id.transactions_footer_text);
    mListView.addFooterView(listFooterParent);

    // Header card swipe
    boolean showBalance = Utils.getPrefsBool(mParentActivity, Constants.KEY_ACCOUNT_SHOW_BALANCE, true);
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
        PreferenceManager.getDefaultSharedPreferences(mParentActivity).edit()
                .putBoolean(Constants.KEY_ACCOUNT_SHOW_BALANCE, false)
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
        PreferenceManager.getDefaultSharedPreferences(mParentActivity).edit()
                .putBoolean(Constants.KEY_ACCOUNT_SHOW_BALANCE, true)
                .commit();
      }
    });

    mListView.setOnScrollListener(new TransactionsInfiniteScrollListener());

    mBalanceText = (TextView) mListHeader.findViewById(R.id.wallet_balance);
    mBalanceHome = (TextView) mListHeader.findViewById(R.id.wallet_balance_home);
    mSyncErrorView = (TextView) mListHeader.findViewById(R.id.wallet_error);

    ((TextView) mBaseView.findViewById(R.id.wallet_balance_label)).setTypeface(
            FontManager.getFont(mParentActivity, "RobotoCondensed-Regular"));
    ((TextView) mBaseView.findViewById(R.id.wallet_send_label)).setTypeface(
           FontManager.getFont(mParentActivity, "RobotoCondensed-Regular"));

    mBalanceText.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Utils.togglePrefsBool(mParentActivity, Constants.KEY_ACCOUNT_BALANCE_FUZZY, true);
        setBalance((Money) v.getTag());
      }
    });

    SQLiteDatabase db = mDbManager.openDatabase();
    try {
      Money oldBalance       = AccountORM.getCachedBalance(db, mLoginManager.getActiveAccountId());
      Money oldNativeBalance = AccountORM.getCachedNativeBalance(db, mLoginManager.getActiveAccountId());
      if (oldBalance != null) {
        setBalance(oldBalance);
        mBalanceText.setTextColor(mParentActivity.getResources().getColor(R.color.wallet_balance_color));
      }
      if (oldNativeBalance != null) {
        mBalanceHome.setText(String.format(mParentActivity.getString(R.string.wallet_balance_home), Utils.formatMoney(oldNativeBalance)));
      }
    } finally {
      mDbManager.closeDatabase();
    }

    if(mBalanceLoading) {
      mBalanceText.setTextColor(mParentActivity.getResources().getColor(R.color.wallet_balance_color_invalid));
    }

    mBaseView.findViewById(R.id.wallet_send).setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        mListener.onSendMoneyClicked();
      }
    });

    loadTransactionsList();

    if (savedInstanceState != null && savedInstanceState.getBoolean("details_showing", false)) {
      mDetailsShowing = true;
      mBaseView.findViewById(R.id.transaction_details_background).setVisibility(View.VISIBLE);
    }

    return mBaseView;
  }

  @Override
  public void onStart() {
    super.onStart();

    // Configure pull to refresh
    mPullToRefreshLayout = new PullToRefreshLayout(mParentActivity);
    AbsDefaultHeaderTransformer ht =
            (AbsDefaultHeaderTransformer) new AbsDefaultHeaderTransformer();
    ActionBarPullToRefresh.from(mParentActivity)
            .insertLayoutInto(mBaseView)
            .theseChildrenArePullable(android.R.id.list)
            .listener(new OnRefreshListener() {
              @Override
              public void onRefreshStarted(View view) {
                mBus.post(new RefreshRequestedEvent());
              }
            })
            .options(Options.create().headerTransformer(ht).build())
            .setup(mPullToRefreshLayout);
    ht.setPullText("Swipe down to refresh");
    ht.setRefreshingText("Refreshing...");

    mBus.register(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    //mPullToRefreshAttacher.onConfigurationChanged(newConfig);
  }

  private void setBalance(Money balance) {
    boolean fuzzy = Utils.getPrefsBool(mParentActivity, Constants.KEY_ACCOUNT_BALANCE_FUZZY, true);
    String balanceString;
    if (fuzzy) {
      balanceString = Utils.formatMoneyRounded(balance);
    } else {
      balanceString = Utils.formatMoney(balance);
    }
    mBalanceText.setText(balanceString);
    mBalanceText.setTag(balance);
  }

  private View getRateNotice() {

    if (mRateNotice != null) {
      return mRateNotice;
    }

    View rateNotice = View.inflate(mParentActivity, R.layout.fragment_transactions_rate_notice, null);

    ((TextView) rateNotice.findViewById(R.id.rate_notice_title)).setTypeface(FontManager.getFont(mParentActivity, "Roboto-Light"));

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

    Constants.RateNoticeState rateNoticeState = Constants.RateNoticeState.valueOf(Utils.getPrefsString(mParentActivity, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN.name()));
    if (rateNoticeState == Constants.RateNoticeState.NOTICE_DISMISSED && !force) {
      return;
    }

    Utils.putPrefsString(mParentActivity, Constants.KEY_ACCOUNT_RATE_NOTICE_STATE, state.name());
    if (getAdapter() != null) {
      getAdapter(InsertedItemListAdapter.class).setInsertedViewVisible(state == Constants.RateNoticeState.SHOULD_SHOW_NOTICE);
      getAdapter().notifyDataSetChanged();
    }
  }

  private void updateBalance() {
    if (mBalanceBtc == null || mBalanceText == null) {
      return; // Not ready yet.
    }

    // Balance is loaded! update the view
    mBalanceLoading = false;

    try {
      SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mParentActivity);

      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        AccountORM.setBalance(db, mLoginManager.getActiveAccountId(), mBalanceBtc);
        AccountORM.setNativeBalance(db, mLoginManager.getActiveAccountId(), mBalanceNative);
      } finally {
        mDbManager.closeDatabase();
      }

      // Update the view.
      mBalanceText.setTextColor(mParentActivity.getResources().getColor(R.color.wallet_balance_color));
      setBalance(mBalanceBtc);
      mBalanceHome.setText(String.format(mNativeBalanceFormatString, Utils.formatMoney(mBalanceNative)));
    } catch (Exception e) {
      e.printStackTrace();
      Bugsnag.notify(new RuntimeException("updateBalance()", e));
    }
  }

  public void refresh() {
    // Make balance appear invalidated
    mBalanceLoading = true;
    mBalanceText.setTextColor(mParentActivity.getResources().getColor(R.color.wallet_balance_color_invalid));

    // Reload transactions + balance
    if(mSyncTask == null) {
      mSyncTask = new SyncTransactionsTask(mParentActivity, null);
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

  public void insertTransactionAnimated(final int insertAtIndex, final TransactionListDisplayItem item) {
    if (!PlatformUtils.hasHoneycomb()) {
      // Do not play animation!
      refresh();
      return;
    }

    mAnimationPlaying = true;
    getListView().setEnabled(false);
    setRateNoticeState(Constants.RateNoticeState.NOTICE_NOT_YET_SHOWN, false);
    getListView().post(new Runnable() {
      @Override
      public void run() {
        getListView().setSelection(0);
        getListView().postDelayed(new Runnable() {
          public void run() {
            _insertTransactionAnimated(insertAtIndex, item);
          }
        }, 500);
      }
    });
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  private void _insertTransactionAnimated(int insertAtIndex, TransactionListDisplayItem item) {

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
    final ImageView fakeListView = new ImageView(mParentActivity);
    fakeListView.setImageBitmap(bitmap);

    Matrix m = new Matrix();
    m.setTranslate(0, -heightToCropOff);
    fakeListView.setImageMatrix(m);
    fakeListView.setScaleType(ImageView.ScaleType.MATRIX);

    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, height);
    params.topMargin = heightToCropOff + getListView().getDividerHeight();
    fakeListView.setLayoutParams(params);

    View newListItem;

    newListItem = View.inflate(mParentActivity, R.layout.fragment_transactions_item, null);

    TextView titleView  = (TextView) newListItem.findViewById(R.id.transaction_title);
    TextView amountView = (TextView) newListItem.findViewById(R.id.transaction_amount);
    TextView statusView = (TextView) newListItem.findViewById(R.id.transaction_status);

    item.configureAmountView(amountView);
    item.configureStatusView(statusView);
    item.configureTitleView(titleView);

    newListItem.setBackgroundColor(Color.WHITE);

    int itemHeight = (int)(70 * metrics.density);
    FrameLayout.LayoutParams itemParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, itemHeight);
    itemParams.topMargin = heightToCropOff + getListView().getDividerHeight(); // account for divider
    newListItem.setLayoutParams(itemParams);

    final View background = new View(mParentActivity);
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
    mItems.add(0, item);
    getAdapter(TransactionsAdapter.class).notifyDataSetChanged();
    refresh();
  }

  @TargetApi(Build.VERSION_CODES.HONEYCOMB)
  public void loadTransactionsList() {
    new LoadTransactionsTask(mParentActivity).execute();
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
    TransactionListDisplayItem i = (TransactionListDisplayItem) l.getItemAtPosition(position);
    i.onClick();
  }

  private void showDetails() {
    mDetailsShowing = true;

    // 1. animate
    getView().findViewById(R.id.transaction_details_background).setVisibility(View.VISIBLE);
    getView().findViewById(R.id.transaction_details_background).startAnimation(
            AnimationUtils.loadAnimation(mParentActivity, R.anim.transactiondetails_bg_enter));
    getView().findViewById(R.id.transaction_details_host).startAnimation(
            AnimationUtils.loadAnimation(mParentActivity, R.anim.transactiondetails_enter));

    // 2. if necessary, change action bar
    mListener.onEnteringDetailsMode();

    // 3. pull to refresh
    mPullToRefreshLayout.setEnabled(false);
  }

  protected void hideDetails(boolean animated) {
    mDetailsShowing = false;

    if(animated) {
      Animation bg = AnimationUtils.loadAnimation(mParentActivity, R.anim.transactiondetails_bg_exit);
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
    mListener.onExitingDetailsMode();

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
    int appUsageCount = Utils.getPrefsInt(mParentActivity, Constants.KEY_ACCOUNT_APP_USAGE, 0);
    if (appUsageCount >= 2 && !mAnimationPlaying) {
      setRateNoticeState(Constants.RateNoticeState.SHOULD_SHOW_NOTICE, false);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    // TODO maybe bind to transaction polling service here in the future
    refresh();
  }

  @Override
  public void onPINPromptSuccessfulReturn() {
    if (mDetailsShowing) {
      ((TransactionDetailsFragment ) getChildFragmentManager().findFragmentById(R.id.transaction_details_host)).onPINPromptSuccessfulReturn();
    }
  }

  @Override
  public String getTitle() {
    return getString(R.string.title_transactions);
  }

  @Subscribe
  public void animateTransaction(TransferMadeEvent transfer) {
    insertTransactionAnimated(0, new TransactionDisplayItem(transfer.transaction));
  }

  @Subscribe
  public void animateDelayedTransaction(NewDelayedTransactionEvent event) {
    insertTransactionAnimated(0, new DelayedTransactionDisplayItem(event.transaction));
  }

  @Subscribe
  public void animateBuySell(BuySellMadeEvent transfer) {
    // TODO animate
    refresh();
  }

  @Subscribe
  public void onRefreshRequested(RefreshRequestedEvent event) {
    refresh();
  }

  @Override
  public void onStop() {
    mBus.unregister(this);
    super.onStop();
  }
}
