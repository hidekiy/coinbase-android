package com.coinbase.android.db;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.coinbase.api.entity.Account;
import com.coinbase.api.entity.AccountChange;
import com.coinbase.api.entity.User;

import org.joda.money.CurrencyUnit;
import org.joda.money.Money;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class AccountORM {
  private static final String TABLE_NAME = "Accounts";

  public static final String COLUMN_ACCOUNT_ID      = "account_id";
  public static final String COLUMN_NAME            = "account_name";
  public static final String COLUMN_RECEIVE_ADDRESS = "receive_address";
  public static final String COLUMN_BALANCE         = "balance";
  public static final String COLUMN_NATIVE_BALANCE  = "native_balance";
  public static final String COLUMN_NATIVE_CURRENCY = "native_currency";

  private static final String COMMA_SEP = ", ";

  public static final String TEXT_TYPE = " TEXT";
  public static final String INTEGER_TYPE = " INTEGER";

  public static final String SQL_CREATE_TABLE =
          "CREATE TABLE " + TABLE_NAME + " (" +
                  COLUMN_ACCOUNT_ID      + TEXT_TYPE    + " NOT NULL" + COMMA_SEP +
                  COLUMN_NAME            + TEXT_TYPE    + " NOT NULL" + COMMA_SEP +
                  COLUMN_RECEIVE_ADDRESS + TEXT_TYPE    +               COMMA_SEP +
                  COLUMN_BALANCE         + TEXT_TYPE    +               COMMA_SEP +
                  COLUMN_NATIVE_BALANCE  + TEXT_TYPE    +               COMMA_SEP +
                  COLUMN_NATIVE_CURRENCY + TEXT_TYPE                              +
                  ")";

  public static final String SQL_DROP_TABLE =
          "DROP TABLE IF EXISTS " + TABLE_NAME;

  public static Account fromCursor(Cursor c) {
    Account result = new Account();

    result.setId(c.getString(c.getColumnIndex(COLUMN_ACCOUNT_ID)));
    result.setName(c.getString(c.getColumnIndex(COLUMN_NAME)));

    return result;
  }

  public static long insert(SQLiteDatabase db, Account account) {
    ContentValues values = new ContentValues();
    values.put(COLUMN_ACCOUNT_ID, account.getId());
    values.put(COLUMN_NAME, account.getName());

    return db.insert(TABLE_NAME, null, values);
  }

  public static void update(SQLiteDatabase db, Account account) {
    ContentValues values = new ContentValues();
    values.put(COLUMN_ACCOUNT_ID, account.getId());
    values.put(COLUMN_NAME, account.getName());

    db.update(
            TABLE_NAME,
            values,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { account.getId() }
    );
  }

  public static String getCachedReceiveAddress(SQLiteDatabase db, String accountId) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { accountId },
            null,
            null,
            null
    );

    if (c.moveToFirst()) {
      return c.getString(c.getColumnIndex(COLUMN_RECEIVE_ADDRESS));
    } else {
      return null;
    }
  }

  public static void setReceiveAddress(SQLiteDatabase db, String accountId, String receiveAddress) {
    ContentValues cv = new ContentValues();
    cv.put(COLUMN_RECEIVE_ADDRESS, receiveAddress);

    db.update(
            TABLE_NAME,
            cv,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[]{accountId}
    );
  }

  public static Money getCachedBalance(SQLiteDatabase db, String accountId) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { accountId },
            null,
            null,
            null
    );

    if (c.moveToFirst()) {
      String balanceString = c.getString(c.getColumnIndex(COLUMN_BALANCE));
      if (balanceString != null) {
        return Money.of(CurrencyUnit.getInstance("BTC"), new BigDecimal(balanceString));
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public static void setBalance(SQLiteDatabase db, String accountId, Money balance) {
    ContentValues cv = new ContentValues();
    cv.put(COLUMN_BALANCE, balance.getAmount().toPlainString());

    db.update(
            TABLE_NAME,
            cv,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[]{accountId}
    );
  }

  public static Money getCachedNativeBalance(SQLiteDatabase db, String accountId) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { accountId },
            null,
            null,
            null
    );

    if (c.moveToFirst()) {
      String balanceString = c.getString(c.getColumnIndex(COLUMN_NATIVE_BALANCE));
      String balanceCurrency = c.getString(c.getColumnIndex(COLUMN_NATIVE_CURRENCY));
      if (balanceString != null && balanceCurrency != null) {
        return Money.of(CurrencyUnit.getInstance(balanceCurrency), new BigDecimal(balanceString));
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public static void setNativeBalance(SQLiteDatabase db, String accountId, Money balance) {
    ContentValues cv = new ContentValues();
    cv.put(COLUMN_NATIVE_BALANCE, balance.getAmount().toPlainString());
    cv.put(COLUMN_NATIVE_CURRENCY, balance.getCurrencyUnit().getCurrencyCode());

    db.update(
            TABLE_NAME,
            cv,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[]{accountId}
    );
  }

  public static List<Account> list(SQLiteDatabase db) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            null,
            null,
            null,
            null,
            COLUMN_ACCOUNT_ID + " DESC"
    );

    ArrayList<Account> result = new ArrayList<Account>();
    c.moveToFirst();
    while (!c.isAfterLast()) {
      result.add(fromCursor(c));
      c.moveToNext();
    }

    return result;
  }

  public static Account find(SQLiteDatabase db, String accountId) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { accountId },
            null,
            null,
            COLUMN_ACCOUNT_ID + " DESC"
    );

    if (c.moveToFirst()) {
      return fromCursor(c);
    } else {
      return null;
    }
  }

  public static long clear(SQLiteDatabase db) {
    return db.delete(TABLE_NAME, null, null);
  }
}
