package com.coinbase.android.task;

import android.content.Context;

import com.coinbase.api.entity.Transaction;

public class CompleteRequestTask extends ApiTask<Transaction> {
  protected String mTxId;

  public CompleteRequestTask(Context context, String txId) {
    super(context);
    mTxId = txId;
  }

  @Override
  public Transaction call() throws Exception {
    return getClient().completeRequest(mTxId);
  }
}
