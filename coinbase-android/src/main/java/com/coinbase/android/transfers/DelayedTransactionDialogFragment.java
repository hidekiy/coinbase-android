package com.coinbase.android.transfers;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import com.coinbase.android.R;
import com.coinbase.android.db.DatabaseManager;
import com.coinbase.android.db.DelayedTransactionORM;
import com.coinbase.android.db.TransactionORM;
import com.coinbase.api.LoginManager;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;

import org.joda.time.DateTime;

import java.util.UUID;

import roboguice.fragment.RoboDialogFragment;

/**
 * Notifies the user that internet is not available and offers to create a delayed transaction.
 */
public class DelayedTransactionDialogFragment extends RoboDialogFragment {

  public static String TRANSACTION = "DelayedTransactionDialogFragment_Transaction";

  private Transaction mTransaction;

  @Inject
  private DatabaseManager mDbManager;

  @Inject
  private LoginManager mLoginManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mTransaction = (Transaction) getArguments().getSerializable(TRANSACTION);
  }

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    return new AlertDialog.Builder(getActivity())
            .setTitle(R.string.delayed_tx_dialog_title)
            .setMessage(R.string.delayed_tx_dialog_message)
            .setPositiveButton(R.string.delayed_tx_dialog_ok, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {
                mTransaction.setCreatedAt(DateTime.now());
                mTransaction.setIdem(UUID.randomUUID().toString());

                SQLiteDatabase db = mDbManager.openDatabase();
                try {
                  DelayedTransactionORM.insert(db, mLoginManager.getActiveAccountId(), mTransaction);
                } finally {
                  mDbManager.closeDatabase();
                }

                // Enable broadcast receiver
                PackageManager pm = getActivity().getPackageManager();
                pm.setComponentEnabledSetting(new ComponentName(getActivity(), ConnectivityChangeReceiver.class),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

                /* TODO switch to transfers and animate

                MainActivity parent = ((MainActivity) getActivity());
                parent.getTransferFragment().clearForm();
                TransactionsFragment transactionsFragment = parent.getTransactionsFragment();
                JSONObject json = delayedTransaction.createTransaction(getActivity());
                transactionsFragment.insertTransactionAnimated(0, json, delayedTransaction.getCategory(), "delayed");
                parent.switchTo(MainActivity.FRAGMENT_INDEX_TRANSACTIONS);

                */
              }
            })
            .setNegativeButton(R.string.delayed_tx_dialog_cancel, new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialogInterface, int i) {

              }
            })
            .create();
  }
}
