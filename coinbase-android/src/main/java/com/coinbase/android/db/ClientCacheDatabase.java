package com.coinbase.android.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

import com.coinbase.api.entity.Transaction;

/**
 * SQLite database that holds cached responses from the API and deferred transactions
 *
 */
public class ClientCacheDatabase extends SQLiteOpenHelper {
  public static final int DATABASE_VERSION = 8;
  public static final String DATABASE_NAME = "coinbase_client_cache";

  public ClientCacheDatabase(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }
  public void onCreate(SQLiteDatabase db) {
    db.execSQL(AccountChangeORM.SQL_CREATE_TABLE);
    db.execSQL(TransactionORM.SQL_CREATE_TABLE);
    db.execSQL(DelayedTransactionORM.SQL_CREATE_TABLE);
    db.execSQL(AccountORM.SQL_CREATE_TABLE);
  }
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    // Transactions will be re-synced; just wipe the database for now
    db.execSQL(AccountChangeORM.SQL_DROP_TABLE);
    db.execSQL(TransactionORM.SQL_DROP_TABLE);
    db.execSQL(DelayedTransactionORM.SQL_DROP_TABLE);
    db.execSQL(AccountChangeORM.SQL_CREATE_TABLE);
    db.execSQL(TransactionORM.SQL_CREATE_TABLE);
    db.execSQL(DelayedTransactionORM.SQL_CREATE_TABLE);
  }
  public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    onUpgrade(db, oldVersion, newVersion);
  }
}
