package com.coinbase.android.event;

import com.coinbase.api.entity.Transfer;

public class BuySellMadeEvent {

  public Transfer transfer;

  public BuySellMadeEvent(Transfer transfer) {
    this.transfer = transfer;
  }

}
