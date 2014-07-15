package com.coinbase.android.event;

import com.coinbase.api.entity.Transaction;

public class NewDelayedTransactionEvent {
  public Transaction transaction;

  public NewDelayedTransactionEvent(Transaction delayedTransaction) {
    this.transaction = delayedTransaction;
  }
}
