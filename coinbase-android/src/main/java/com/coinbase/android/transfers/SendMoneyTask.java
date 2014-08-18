package com.coinbase.android.transfers;

import android.content.Context;
import android.widget.Toast;

import com.coinbase.android.R;
import com.coinbase.android.Utils;
import com.coinbase.android.event.TransferMadeEvent;
import com.coinbase.android.task.ApiTask;
import com.coinbase.api.entity.Transaction;
import com.google.inject.Inject;
import com.squareup.otto.Bus;

import org.joda.money.Money;

import java.math.BigDecimal;

import roboguice.inject.InjectResource;

class SendMoneyTask extends ApiTask<Transaction> {
  @Inject
  protected Bus mBus;

  @InjectResource(R.string.transfer_success_send)
  protected String successMessage;

  Money mAmount, mFee;
  String mRecipient, mNotes;

  public SendMoneyTask(Context context, String recipient, Money amount, Money fee, String notes) {
    super(context);
    mAmount = amount;
    mRecipient = recipient;
    mFee = fee;
    mNotes = notes;
  }

  @Override
  public Transaction call() throws Exception {
    Transaction sendMoney = new Transaction();
    sendMoney.setTo(mRecipient);
    sendMoney.setNotes(mNotes);
    sendMoney.setAmount(mAmount);

    if (mFee != null) {
      if (!mFee.getCurrencyUnit().getCurrencyCode().equalsIgnoreCase("BTC")) {
        throw new AssertionError();
      }
      if (mFee.getAmount().compareTo(BigDecimal.ZERO) > 0) {
        sendMoney.setUserFee(mFee.getAmount());
      }
    }

    return getClient().sendMoney(sendMoney);
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
