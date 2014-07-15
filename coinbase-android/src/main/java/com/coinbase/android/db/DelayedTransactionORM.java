package com.coinbase.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import com.coinbase.api.entity.Transaction;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DelayedTransactionORM implements BaseColumns {

  private static final String TABLE_NAME = "DelayedTransactions";

  public static final String COLUMN_ACCOUNT_ID = "account_id";
  public static final String COLUMN_AMOUNT_STRING = "amount";
  public static final String COLUMN_AMOUNT_CURRENCY = "amount_currency";
  public static final String COLUMN_FROM = "tx_from";
  public static final String COLUMN_TO = "tx_to";
  public static final String COLUMN_IS_REQUEST = "is_request";
  public static final String COLUMN_NOTES = "notes";
  public static final String COLUMN_IDEM = "idem";
  public static final String COLUMN_CREATED_AT = "created_at";

  private static final String COMMA_SEP = ", ";

  public static final String TEXT_TYPE = " TEXT";
  public static final String INTEGER_TYPE = " INTEGER";

  public static final String SQL_CREATE_TABLE =
          "CREATE TABLE " + TABLE_NAME + " (" +
                  _ID                      + INTEGER_TYPE + " PRIMARY KEY AUTOINCREMENT NOT NULL" + COMMA_SEP +
                  COLUMN_ACCOUNT_ID        + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_AMOUNT_STRING     + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_AMOUNT_CURRENCY   + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_FROM              + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_TO                + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_IS_REQUEST        + INTEGER_TYPE + COMMA_SEP +
                  COLUMN_NOTES             + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_IDEM              + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_CREATED_AT        + INTEGER_TYPE +
                  ")";

  public static final String SQL_DROP_TABLE =
          "DROP TABLE IF EXISTS " + TABLE_NAME;

  public static ContentValues toContentValues(String accountId, Transaction tx) {
    ContentValues values = new ContentValues();

    values.put(COLUMN_ACCOUNT_ID, accountId);
    Money amount = tx.getAmount();
    if (amount != null) {
      values.put(COLUMN_AMOUNT_STRING, amount.getAmount().toPlainString());
      values.put(COLUMN_AMOUNT_CURRENCY, amount.getCurrencyUnit().getCurrencyCode());
    }
    values.put(COLUMN_NOTES, tx.getNotes());
    values.put(COLUMN_CREATED_AT, tx.getCreatedAt().getMillis());
    if (tx.getTo() != null) {
      values.put(COLUMN_TO, tx.getTo());
    }
    if (tx.getFrom() != null) {
      values.put(COLUMN_FROM, tx.getFrom());
    }

    values.put(COLUMN_IS_REQUEST, tx.isRequest() ? 1 : 0);

    values.put(COLUMN_IDEM, tx.getIdem());

    return values;
  }

  public static Transaction fromCursor(Cursor c) {
    Transaction result = new Transaction();

    result.setId(c.getString(c.getColumnIndex(_ID)));
    result.setFrom(c.getString(c.getColumnIndex(COLUMN_FROM)));
    result.setTo(c.getString(c.getColumnIndex(COLUMN_TO)));

    result.setCreatedAt(new DateTime(c.getLong(c.getColumnIndex(COLUMN_CREATED_AT))));

    String currencyCode = c.getString(c.getColumnIndex(COLUMN_AMOUNT_CURRENCY));
    String amountString = c.getString(c.getColumnIndex(COLUMN_AMOUNT_STRING));
    if (currencyCode != null && amountString != null) {
      BigDecimal amount = new BigDecimal(amountString);
      result.setAmount(Money.of(CurrencyUnit.getInstance(currencyCode), amount));
    }

    result.setNotes(c.getString(c.getColumnIndex(COLUMN_NOTES)));

    result.setRequest(c.getInt(c.getColumnIndex(COLUMN_IS_REQUEST)) != 0);

    result.setIdem(c.getString(c.getColumnIndex(COLUMN_IDEM)));

    return result;
  }

  public static void insert(SQLiteDatabase db, String accountId, Transaction tx) {
    db.insert(
            TABLE_NAME,
            _ID,
            toContentValues(accountId, tx)
    );
  }

  public static void delete(SQLiteDatabase db, Transaction tx) {
    db.delete(
            TABLE_NAME,
            _ID + " = ?",
            new String[]{ tx.getId() }
    );
  }

  public static Transaction find(SQLiteDatabase db, String id) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            _ID + " = ?",
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

  public static List<Transaction> getTransactions(SQLiteDatabase db, String accountId) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { accountId },
            null,
            null,
            COLUMN_CREATED_AT + " DESC"
    );

    ArrayList<Transaction> result = new ArrayList<Transaction>();
    c.moveToFirst();
    while (!c.isAfterLast()) {
      result.add(fromCursor(c));
      c.moveToNext();
    }

    return result;
  }
}
