package com.coinbase.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import com.coinbase.api.entity.Transaction;
import com.coinbase.api.entity.User;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class TransactionORM implements BaseColumns {

  private static final String TABLE_NAME = "Transactions";

  public static final String COLUMN_ACCOUNT_ID = "account_id";
  public static final String COLUMN_TRANSACTION_ID = "transaction_id";
  public static final String COLUMN_AMOUNT_STRING = "amount";
  public static final String COLUMN_AMOUNT_CURRENCY = "amount_currency";
  public static final String COLUMN_SENDER_ID = "sender_id";
  public static final String COLUMN_SENDER_NAME = "sender_name";
  public static final String COLUMN_SENDER_EMAIL = "sender_email";
  public static final String COLUMN_RECIPIENT_ID = "recipient_id";
  public static final String COLUMN_RECIPIENT_NAME = "recipient_name";
  public static final String COLUMN_RECIPIENT_EMAIL = "recipient_email";
  public static final String COLUMN_IS_REQUEST = "is_request";
  public static final String COLUMN_CONFIRMATIONS = "confirmations";
  public static final String COLUMN_STATUS = "status";
  public static final String COLUMN_NOTES = "notes";
  public static final String COLUMN_CREATED_AT = "created_at";

  private static final String COMMA_SEP = ", ";

  public static final String TEXT_TYPE = " TEXT";
  public static final String INTEGER_TYPE = " INTEGER";

  public static final String SQL_CREATE_TABLE =
          "CREATE TABLE " + TABLE_NAME + " (" +
                  _ID                      + INTEGER_TYPE + " PRIMARY KEY AUTOINCREMENT NOT NULL" + COMMA_SEP +
                  COLUMN_ACCOUNT_ID        + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_TRANSACTION_ID    + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_AMOUNT_STRING     + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_AMOUNT_CURRENCY   + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_SENDER_ID         + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_SENDER_NAME       + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_SENDER_EMAIL      + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_RECIPIENT_ID      + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_RECIPIENT_NAME    + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_RECIPIENT_EMAIL   + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_IS_REQUEST        + INTEGER_TYPE + COMMA_SEP +
                  COLUMN_CONFIRMATIONS     + INTEGER_TYPE + COMMA_SEP +
                  COLUMN_STATUS            + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_NOTES             + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_CREATED_AT        + INTEGER_TYPE +
          ")";

  public static final String SQL_DROP_TABLE =
          "DROP TABLE IF EXISTS " + TABLE_NAME;

  public static ContentValues toContentValues(String accountId, Transaction tx, boolean delayed) {
    ContentValues values = new ContentValues();

    values.put(COLUMN_ACCOUNT_ID, accountId);
    values.put(COLUMN_TRANSACTION_ID, tx.getId());
    Money amount = tx.getAmount();
    if (amount != null) {
      values.put(COLUMN_AMOUNT_STRING, amount.getAmount().toPlainString());
      values.put(COLUMN_AMOUNT_CURRENCY, amount.getCurrencyUnit().getCurrencyCode());
    }
    values.put(COLUMN_NOTES, tx.getNotes());
    values.put(COLUMN_CREATED_AT, tx.getCreatedAt().getMillis());
    if (tx.getRecipient() != null) {
      values.put(COLUMN_RECIPIENT_ID, tx.getRecipient().getId());
      values.put(COLUMN_RECIPIENT_EMAIL, tx.getRecipient().getEmail());
      values.put(COLUMN_RECIPIENT_NAME, tx.getRecipient().getName());
    }
    if (tx.getSender() != null) {
      values.put(COLUMN_SENDER_ID, tx.getSender().getId());
      values.put(COLUMN_SENDER_EMAIL, tx.getSender().getEmail());
      values.put(COLUMN_SENDER_NAME, tx.getSender().getName());
    }

    values.put(COLUMN_IS_REQUEST, tx.isRequest() ? 1 : 0);
    values.put(COLUMN_CONFIRMATIONS, tx.getConfirmations());
    values.put(COLUMN_STATUS, tx.getStatus().toString());

    return values;
  }

  public static Transaction fromCursor(Cursor c) {
    Transaction result = new Transaction();

    result.setId(c.getString(c.getColumnIndex(COLUMN_TRANSACTION_ID)));

    User recipient = new User();
    recipient.setId(c.getString(c.getColumnIndex(COLUMN_RECIPIENT_ID)));
    recipient.setName(c.getString(c.getColumnIndex(COLUMN_RECIPIENT_NAME)));
    recipient.setEmail(c.getString(c.getColumnIndex(COLUMN_RECIPIENT_EMAIL)));
    result.setRecipient(recipient);

    User sender    = new User();
    sender.setId(c.getString(c.getColumnIndex(COLUMN_SENDER_ID)));
    sender.setName(c.getString(c.getColumnIndex(COLUMN_SENDER_NAME)));
    sender.setEmail(c.getString(c.getColumnIndex(COLUMN_SENDER_EMAIL)));
    result.setSender(sender);

    result.setCreatedAt(new DateTime(c.getLong(c.getColumnIndex(COLUMN_CREATED_AT))));

    String currencyCode = c.getString(c.getColumnIndex(COLUMN_AMOUNT_CURRENCY));
    String amountString = c.getString(c.getColumnIndex(COLUMN_AMOUNT_STRING));
    if (currencyCode != null && amountString != null) {
      BigDecimal amount = new BigDecimal(amountString);
      result.setAmount(Money.of(CurrencyUnit.getInstance(currencyCode), amount));
    }

    result.setNotes(c.getString(c.getColumnIndex(COLUMN_NOTES)));

    result.setRequest(c.getInt(c.getColumnIndex(COLUMN_IS_REQUEST)) != 0);

    result.setConfirmations(c.getInt(c.getColumnIndex(COLUMN_CONFIRMATIONS)));

    result.setStatus(Transaction.Status.create(c.getString(c.getColumnIndex(COLUMN_STATUS))));

    return result;
  }

  public static void insertOrUpdate(SQLiteDatabase db, String accountId, Transaction tx) {
    db.delete(
            TABLE_NAME,
            COLUMN_TRANSACTION_ID + " = ?",
            new String[] { tx.getId() }
    );

    db.insert(
            TABLE_NAME,
            _ID,
            toContentValues(accountId, tx, false)
    );
  }

  public static Transaction find(SQLiteDatabase db, String id) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_TRANSACTION_ID + " = ?",
            new String[] { id },
            null,
            null,
            COLUMN_CREATED_AT + " DESC"
    );

    if (c.moveToFirst()) {
      return fromCursor(c);
    } else {
      return null;
    }
  }
}
