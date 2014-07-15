package com.coinbase.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.coinbase.android.db.AccountChangeORM;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.api.entity.AccountChange;
import com.google.inject.Inject;

import org.joda.money.Money;

import java.util.List;

import roboguice.RoboGuice;

// TODO Log improvement from master

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class TransactionsRemoteViewsService extends RemoteViewsService {

  public static final String WIDGET_TRANSACTION_LIMIT = "10";
  public static final String ACCOUNT_ID = "account_id";

  public class TransactionsRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {

    @Inject
    DatabaseManager mDbManager;

    Context mContext;

    String mAccountId;

    List<AccountChange> mAccountChanges;

    public TransactionsRemoteViewsFactory(Context context, String accountId) {
      mContext = context;
      mAccountId = accountId;
      RoboGuice.getInjector(context).injectMembers(this);
    }

    @Override
    public void onCreate() {
      query();
    }

    private void query() {
      Log.i("Coinbase", "Filtering account changes for account " + mAccountId);
      SQLiteDatabase db = mDbManager.openDatabase();
      try {
        mAccountChanges = AccountChangeORM.getOrderedAccountChanges(db, mAccountId);
        Log.i("Coinbase", "Got " + mAccountChanges.size() + " accountChanges.");
      } finally {
        mDbManager.closeDatabase();
      }
    }

    @Override
    public int getCount() {
      return mAccountChanges.size();
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public RemoteViews getLoadingView() {
      return null;
    }

    @Override
    public RemoteViews getViewAt(int position) {
      AccountChange accountChange = mAccountChanges.get(position);

      RemoteViews rv = new RemoteViews(mContext.getPackageName(), R.layout.widget_transactions_item);

      // Amount:
      Money amount = accountChange.getAmount();
      String amountString = Utils.formatMoney(amount);

      int color;
      if (amount.isZero()) {
        color = R.color.transaction_neutral;
      } else if (amount.isPositive()) {
        color = R.color.transaction_positive;
      } else {
        color = R.color.transaction_negative;
      }

      rv.setTextViewText(R.id.transaction_amount, amountString);
      rv.setTextColor(R.id.transaction_amount, mContext.getResources().getColor(color));

      // Title:
      rv.setTextViewText(R.id.transaction_title, Utils.generateAccountChangeSummary(mContext, accountChange));

      // Status:
      String readable;
      int background;
      if (accountChange.isConfirmed() == null) {
        readable = getString(R.string.transaction_status_error);
        background = R.drawable.transaction_unknown;
      } else if (accountChange.isConfirmed()) {
        readable = getString(R.string.transaction_status_complete);
        background = R.drawable.transaction_complete;
      } else {
        readable = getString(R.string.transaction_status_pending);
        background = R.drawable.transaction_pending;
      }

      rv.setTextViewText(R.id.transaction_status, readable);
      rv.setInt(R.id.transaction_status, "setBackgroundResource", background);

      if (accountChange.getTransactionId() != null) {
        Intent intent = new Intent();
        intent.putExtra(TransactionDetailsFragment.ID, accountChange.getTransactionId());
        rv.setOnClickFillInIntent(R.id.transactions_item, intent);
      }

      return rv;
    }

    @Override
    public int getViewTypeCount() {
      return 1;
    }

    @Override
    public boolean hasStableIds() {
      return false;
    }

    @Override
    public void onDataSetChanged() {
      query();
    }

    @Override
    public void onDestroy() {

    }
  }

  @Override
  public RemoteViewsFactory onGetViewFactory(Intent intent) {
    return new TransactionsRemoteViewsFactory(getApplicationContext(), intent.getStringExtra(ACCOUNT_ID));
  }

}
