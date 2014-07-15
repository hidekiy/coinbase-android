package com.coinbase.android.event;

import com.coinbase.api.entity.Transaction;

public class TransferMadeEvent {

  public Transaction transaction;

  public TransferMadeEvent(Transaction transaction) {
    this.transaction = transaction;
  }

}
