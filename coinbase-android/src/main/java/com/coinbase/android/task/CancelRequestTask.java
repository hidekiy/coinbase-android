package com.coinbase.android.task;

import android.content.Context;

public class CancelRequestTask extends ApiTask<Void> {
  protected String mTxId;

  public CancelRequestTask(Context context, String txId) {
    super(context);
    mTxId = txId;
  }

  @Override
  public Void call() throws Exception {
    getClient().deleteRequest(mTxId);
    return null;
  }
}
