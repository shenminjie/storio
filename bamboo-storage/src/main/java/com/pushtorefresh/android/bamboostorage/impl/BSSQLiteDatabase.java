package com.pushtorefresh.android.bamboostorage.impl;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import com.pushtorefresh.android.bamboostorage.BambooStorage;
import com.pushtorefresh.android.bamboostorage.operation.delete.PreparedDelete;
import com.pushtorefresh.android.bamboostorage.operation.get.PreparedGet;
import com.pushtorefresh.android.bamboostorage.operation.put.PreparedPut;
import com.pushtorefresh.android.bamboostorage.query.DeleteQuery;
import com.pushtorefresh.android.bamboostorage.query.InsertQuery;
import com.pushtorefresh.android.bamboostorage.query.Query;
import com.pushtorefresh.android.bamboostorage.query.RawQuery;
import com.pushtorefresh.android.bamboostorage.query.UpdateQuery;

import java.util.Set;

import rx.Observable;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

public class BSSQLiteDatabase implements BambooStorage {

    /**
     * Real db
     */
    @NonNull private final SQLiteDatabase db;

    /**
     * Reactive bus for notifying observers about changes in tables
     * One change can affect several tables, so we use Set<String> as set of changed tables per event
     */
    @NonNull private final PublishSubject<Set<String>> tablesMonitor = PublishSubject.create();

    /**
     * Implementation of {@link com.pushtorefresh.android.bamboostorage.BambooStorage.Internal}
     */
    @NonNull private final Internal internal = new InternalImpl();

    protected BSSQLiteDatabase(@NonNull SQLiteDatabase db) {
        this.db = db;
    }

    @NonNull @Override public PreparedGet.Builder get() {
        return new PreparedGet.Builder(this);
    }

    @NonNull @Override public PreparedPut.Builder put() {
        return new PreparedPut.Builder(this);
    }

    @NonNull @Override public PreparedDelete.Builder delete() {
        return new PreparedDelete.Builder(this);
    }

    @Override @NonNull
    public Observable<Set<String>> subscribeOnChanges(@NonNull final Set<String> tables) {
        return tablesMonitor
                .filter(new Func1<Set<String>, Boolean>() {
                    @Override public Boolean call(Set<String> changedTables) {
                        // if one of changed tables found in tables for subscription -> notify observer
                        for (final String changedTable : changedTables) {
                            if (tables.contains(changedTable)) {
                                return true;
                            }
                        }

                        return false; // ignore changes from current event
                    }
                });
    }

    @NonNull @Override public Internal internal() {
        return internal;
    }

    protected class InternalImpl implements Internal {

        @NonNull @Override public Cursor rawQuery(@NonNull RawQuery rawQuery) {
            return db.rawQuery(
                    rawQuery.query,
                    rawQuery.args
            );
        }

        @NonNull @Override public Cursor query(@NonNull Query query) {
            return db.query(
                    query.distinct,
                    query.table,
                    query.columns,
                    query.selection,
                    query.whereArgs,
                    query.groupBy,
                    query.having,
                    query.orderBy,
                    query.limit
            );
        }

        @Override
        public long insert(@NonNull InsertQuery insertQuery, @NonNull ContentValues contentValues) {
            return db.insertOrThrow(
                    insertQuery.table,
                    insertQuery.nullColumnHack,
                    contentValues
            );
        }

        @Override
        public int update(@NonNull UpdateQuery updateQuery, @NonNull ContentValues contentValues) {
            return db.update(
                    updateQuery.table,
                    contentValues,
                    updateQuery.where,
                    updateQuery.whereArgs
            );
        }

        @Override public int delete(@NonNull DeleteQuery deleteQuery) {
            return db.delete(
                    deleteQuery.table,
                    deleteQuery.where,
                    deleteQuery.whereArgs
            );
        }

        @Override public void notifyAboutChanges(@NonNull Set<String> affectedTables) {
            tablesMonitor.onNext(affectedTables);
        }

        @Override public boolean areTransactionsSupported() {
            return true;
        }

        @Override public void beginTransaction() {
            db.beginTransaction();
        }

        @Override public void setTransactionSuccessful() {
            db.setTransactionSuccessful();
        }

        @Override public void endTransaction() {
            db.endTransaction();
        }
    }

    public static class Builder {

        private SQLiteDatabase db;

        @NonNull public Builder db(@NonNull SQLiteDatabase db) {
            this.db = db;
            return this;
        }

        @NonNull public Builder sqliteOpenHelper(@NonNull SQLiteOpenHelper sqliteOpenHelper) {
            db = sqliteOpenHelper.getWritableDatabase();
            return this;
        }

        @NonNull public BSSQLiteDatabase build() {
            if (db == null) {
                throw new IllegalStateException("Please specify SQLiteDatabase instance");
            }

            return new BSSQLiteDatabase(db);
        }
    }
}
