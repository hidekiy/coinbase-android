package com.coinbase.android.db;

import android.app.Application;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class DatabaseManager {

  private AtomicInteger mOpenCounter = new AtomicInteger();
  private ClientCacheDatabase mClientCacheDatabase;
  private SQLiteDatabase mDatabase;

  @Inject
  public DatabaseManager (Application applicationContext) {
    mClientCacheDatabase = new ClientCacheDatabase(applicationContext);
  }

  public synchronized SQLiteDatabase openDatabase() {
    if(mOpenCounter.incrementAndGet() == 1) {
      // Opening new database
      mDatabase = mClientCacheDatabase.getWritableDatabase();
    }
    return mDatabase;
  }

  public synchronized void closeDatabase() {
    if (mOpenCounter.decrementAndGet() == 0) {
      // Closing database
      mDatabase.close();
    }
  }

}
