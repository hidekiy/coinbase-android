package com.coinbase.android.db;

import android.provider.BaseColumns;

public class TransactionORM implements BaseColumns {

  private static final String TABLE_NAME = "Transactions";

  public static final String COLUMN_ACCOUNT_ID = "account_id";
  public static final String COLUMN_TRANSACTION_ID = "transaction_id";
  public static final String COLUMN_AMOUNT_STRING = "amount";
  public static final String COLUMN_AMOUNT_CURRENCY = "amount_currency";
  public static final String COLUMN_SENDER_NAME = "sender_name";
  public static final String COLUMN_SENDER_EMAIL = "sender_email";
  public static final String COLUMN_RECIPIENT_NAME = "recipient_name";
  public static final String COLUMN_RECIPIENT_EMAIL = "recipient_email";
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
                  COLUMN_SENDER_NAME       + INTEGER_TYPE + COMMA_SEP +
                  COLUMN_SENDER_EMAIL      + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_RECIPIENT_NAME    + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_RECIPIENT_EMAIL   + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_STATUS            + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_NOTES             + TEXT_TYPE    + COMMA_SEP +
                  COLUMN_CREATED_AT        + INTEGER_TYPE +
          ")";

  public static final String SQL_DROP_TABLE =
          "DROP TABLE IF EXISTS " + TABLE_NAME;

  /*
  public static ContentValues toContentValues(String accountId, Transaction tx) {
    AccountChange.Cache cache = change.getCache();

    ContentValues values = new ContentValues();
    values.put(COLUMN_ACCOUNT_ID, accountId);
    if (cache != null) {
      values.put(COLUMN_CATEGORY, cache.getCategory().toString());
      if (cache.getOtherUser() != null) {
        values.put(COLUMN_COUNTERPARTY_NAME, cache.getOtherUser().getName());
      }
    }
    if (change.getTransactionId() != null) {
      values.put(COLUMN_TRANSACTION_ID, change.getTransactionId());
    }
    Money amount = change.getAmount();
    if (amount != null) {
      values.put(COLUMN_AMOUNT_STRING, amount.getAmount().toPlainString());
      values.put(COLUMN_AMOUNT_CURRENCY, amount.getCurrencyUnit().getCurrencyCode());
    }
    values.put(COLUMN_CONFIRMED, change.isConfirmed());
    values.put(COLUMN_CREATED_AT, change.getCreatedAt().getMillis());

    return values;
  }

  public static AccountChange fromCursor(Cursor c) {
    AccountChange result = new AccountChange();
    AccountChange.Cache cache = new AccountChange.Cache();
    result.setCache(cache);

    result.setCreatedAt(new DateTime(c.getLong(c.getColumnIndex(COLUMN_CREATED_AT))));

    String currencyCode = c.getString(c.getColumnIndex(COLUMN_AMOUNT_CURRENCY));
    String amountString = c.getString(c.getColumnIndex(COLUMN_AMOUNT_STRING));
    if (currencyCode != null && amountString != null) {
      BigDecimal amount = new BigDecimal(amountString);
      result.setAmount(Money.of(CurrencyUnit.getInstance(currencyCode), amount));
    }
    result.setConfirmed(c.getInt(c.getColumnIndex(COLUMN_CONFIRMED)) != 0);
    result.setTransactionId(c.getString(c.getColumnIndex(COLUMN_TRANSACTION_ID)));

    String categoryString = c.getString(c.getColumnIndex(COLUMN_CATEGORY));
    if (categoryString != null) {
      cache.setCategory(AccountChange.Cache.Category.create(categoryString));
    }

    String counterpartyName = c.getString(c.getColumnIndex(COLUMN_COUNTERPARTY_NAME));
    if (counterpartyName != null) {
      User user = new User();
      user.setName(counterpartyName);
      cache.setOtherUser(user);
    }

    return result;
  }

  public static List<AccountChange> getOrderedAccountChanges(SQLiteDatabase db, String accountId) {
    Cursor c = db.query(
            TABLE_NAME,
            null,
            COLUMN_ACCOUNT_ID + " = ?",
            new String[] { accountId },
            null,
            null,
            COLUMN_CREATED_AT + " DESC"
    );

    ArrayList<AccountChange> result = new ArrayList<AccountChange>();
    c.moveToFirst();
    do {
      result.add(fromCursor(c));
      c.moveToNext();
    } while (!c.isAfterLast());

    return result;
  }

  public static long insert(SQLiteDatabase db, String accountId, AccountChange change) {
    return db.insert(TABLE_NAME, null, toContentValues(accountId, change));
  }

  public static long clear(SQLiteDatabase db, String accountId) {
    return db.delete(TABLE_NAME, "WHERE " + COLUMN_ACCOUNT_ID + " = ?", new String[] { accountId });
  }

*/

}
