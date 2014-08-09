package com.coinbase.android;

public interface TransactionsFragmentListener {
  public void onSendMoneyClicked();
  public void onStartTransactionsSync();
  public void onFinishTransactionsSync();
}
