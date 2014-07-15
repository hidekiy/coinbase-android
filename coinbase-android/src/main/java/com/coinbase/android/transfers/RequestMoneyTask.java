package com.coinbase.android.transfers;

import android.content.Context;
import android.widget.Toast;

import com.coinbase.android.R;
import com.coinbase.android.event.TransferMadeEvent;
import com.coinbase.android.task.ApiTask;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

import org.joda.money.Money;

import roboguice.inject.InjectResource;

class RequestMoneyTask extends ApiTask<Transaction> {
  @Inject
  protected Bus mBus;

  @InjectResource(R.string.transfer_success_request)
  protected String successMessage;

  Money mAmount;
  String mRecipient, mNotes;

  public RequestMoneyTask(Context context, String recipient, Money amount, String notes) {
    super(context);
    mAmount = amount;
    mRecipient = recipient;
    mNotes = notes;
  }

  @Override
  public Transaction call() throws Exception {
    Transaction requestMoney = new Transaction();
    requestMoney.setFrom(mRecipient);
    requestMoney.setNotes(mNotes);
    requestMoney.setAmount(mAmount);
    return getClient().requestMoney(requestMoney);
  }


  @Override
  public void onSuccess(Transaction transaction) {
    String text = String.format(successMessage, transaction.getAmount().abs().getAmount().toPlainString(), mRecipient);
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    mBus.post(new TransferMadeEvent(transaction));
  }

  @Override
  public void onException(Exception ex) {
    super.onException(ex);
    Toast.makeText(context, ex.getMessage(), Toast.LENGTH_SHORT).show();
  }
}
