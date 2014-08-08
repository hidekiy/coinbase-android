package com.coinbase.android.task;

import android.content.Context;

import com.coinbase.api.entity.Transaction;

public class FetchTransactionTask extends ApiTask<Transaction> {
  protected String mTxId;

  public FetchTransactionTask(Context context, String txId) {
    super(context);
    mTxId = txId;
  }

  @Override
  public Transaction call() throws Exception {
    return getClient().getTransaction(mTxId);
  }
}
