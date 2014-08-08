package com.coinbase.android.task;

import android.content.Context;

import com.coinbase.android.task.ApiTask;
import com.coinbase.api.entity.Transaction;

import java.util.List;

public class GetLatestTransactionsTask extends ApiTask<List<Transaction>> {
  public GetLatestTransactionsTask(Context context) {
    super(context);
  }

  @Override
  public List<Transaction> call() throws Exception {
    return getClient().getTransactions().getTransactions();
  }
}
