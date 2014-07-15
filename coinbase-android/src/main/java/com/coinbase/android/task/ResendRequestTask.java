package com.coinbase.android.task;

import android.content.Context;

public class ResendRequestTask extends ApiTask<Void> {
  protected String mTxId;

  public ResendRequestTask(Context context, String txId) {
    super(context);
    mTxId = txId;
  }

  @Override
  public Void call() throws Exception {
    getClient().resendRequest(mTxId);
    return null;
  }
}
