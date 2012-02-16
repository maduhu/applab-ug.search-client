/**
 * Copyright (C) 2010 Grameen Foundation
Licensed under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
 */

package applab.search.client;

import java.util.HashMap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import applab.client.search.R;

/**
 * An adapter for the search keywords database
 */
public class Storage {
    /* Menu Table Columns */
    public static final String MENU_ROWID_COLUMN = "id";
    public static final String MENU_LABEL_COLUMN = "label";

    /* Menu Item Table Columns */
    public static final String MENU_ITEM_ROWID_COLUMN = "id";
    public static final String MENU_ITEM_LABEL_COLUMN = "label";
    public static final String MENU_ITEM_POSITION_COLUMN = "position";
    public static final String MENU_ITEM_CONTENT_COLUMN = "content";
    public static final String MENU_ITEM_MENUID_COLUMN = "menu_id";
    public static final String MENU_ITEM_PARENTID_COLUMN = "parent_id";
    public static final String MENU_ITEM_ATTACHMENTID_COLUMN = "attachment_id";


    private static final String DATABASE_NAME = "search";
    private static final int DATABASE_VERSION = 5;
    private static final int SEQUENCES = 32;

    /** keep track of batch size to enable batch inserts **/
    private Integer currentBatchSize = 0;
    private static final Integer MAX_BATCH_SIZE = 200;

    private DatabaseHelper databaseHelper;
    private SQLiteDatabase database;

    /** the application context in which we are working */
    private final Context context;

    public Storage(Context context) {
        this.context = context;
    }

    /**
     * Attempt to open @DATABASE_NAME database
     *
     * @return Database object
     * @throws SQLException
     */
    public Storage open() throws SQLException {
        this.databaseHelper = new DatabaseHelper(context);
        this.database = databaseHelper.getWritableDatabase();
        return this;
    }

    /**
     * Disconnect database
     */
    public void close() {
        if(database.inTransaction()) {
            database.setTransactionSuccessful();
            database.endTransaction();
        }
        databaseHelper.close();
    }

    /**
     * Select search menu options. Options are the search menu items that the user can select from during a search
     * activity. e.g. Animals, Crops, Farm Inputs, Regional Weather Info are menu options.
     *
     * @param table
     *            the currently active table to query
     * @param optionColumn
     *            the search keywords table field
     * @param condition
     *            the conditional SQL string
     * @return A cursor pointing before the first element of the result set.
     */
    public Cursor selectMenuOptions(String table, String optionColumn, String condition) {
        // database.rawQuery("SELECT DISTINCT(" + optionColumn + ") FROM " + table + " WHERE ", selectionArgs)
        return database.query(true, table, new String[] { optionColumn }, condition,
                null, null, null, " MAX(" + MENU_ITEM_POSITION_COLUMN + ") DESC, " + optionColumn + " ASC", null);
    }

    public HashMap<String, String> selectContent(String table, String condition) {
        Cursor cursor = null;
        HashMap<String, String> results = new HashMap<String, String>();
        try {
            cursor = database.query(true, table, new String[] {"content", "attribution", "updated"}, condition,
                    null, null, null, null, null);
            cursor.moveToFirst();
            Integer contentIndex = cursor.getColumnIndexOrThrow("content");
            String content = cursor.getString(contentIndex);

            Integer attributionIndex = cursor.getColumnIndexOrThrow("attribution");
            String attribution = cursor.getString(attributionIndex);

            Integer updatedIndex = cursor.getColumnIndexOrThrow("updated");
            String updated = cursor.getString(updatedIndex);

            results.put("content", content);
            results.put("attribution", attribution);
            results.put("updated", updated);

            return results;
        }
        finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    public boolean insertContent(String table, ContentValues values) {
        return database.replace(table, null, values) > 0;
    }

    public boolean deleteEntryInBatch(String table, String rowIdColumn, String id) {
        // Begin a transaction if we're not yet in one
        if(!database.inTransaction()) {
            database.beginTransaction();
        }

        Boolean successful = deleteEntry(table, rowIdColumn, id);

        // Increment the currentBatchSize
        currentBatchSize++;

        // Write all the previous data
        if((currentBatchSize > MAX_BATCH_SIZE) && database.inTransaction()) {
            database.setTransactionSuccessful();
            database.endTransaction();
            currentBatchSize = 0;
        }

        return successful;

        // Note: remember to call storage.close() - it will end any pending transactions, in case there are < MAX_BATCH_SIZE values in the batch
    }

    Boolean deleteEntry(String table, String rowIdColumn, String id) {
        return database.delete(table, rowIdColumn + "=" + id, null) > 0;
    }

    public boolean insertContentInBatch(String table, ContentValues values) {
        // Begin a transaction if we're not yet in one
        if(!database.inTransaction()) {
            database.beginTransaction();
        }

        // Add the current values
        Boolean successful = insertContent(table, values);

        // Increment the currentBatchSize
        currentBatchSize++;

        // Write all the previous data
        if((currentBatchSize > MAX_BATCH_SIZE) && database.inTransaction()) {
            database.setTransactionSuccessful();
            database.endTransaction();
            currentBatchSize = 0;
        }

        return successful;

        // Note: remember to call storage.close() - it will end any pending transactions, in case there are < MAX_BATCH_SIZE values in the batch
    }

    /**
     * Remove all table rows
     *
     * @return the number of rows affected
     */
    public int deleteAll(String table) {
        return database.delete(table, null, null);
    }

    /**
     * checks if the given table exists and has valid data.
     */
    public boolean tableExistsAndIsValid(String table, String idColumn, String labelColumn) {
        Cursor cursor = database.query(table, new String[] { idColumn}, null,
                null, null, null, null, "1");
        boolean isValid = false;
        if (cursor.moveToFirst()) {
            // simple validation: check if the label column is not null
            int columnIndex = cursor.getColumnIndexOrThrow(labelColumn);
            if (cursor.getString(columnIndex) != null) {
                isValid = true;
            }
        }
        cursor.close();
        return isValid;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            // Create Menu Table
            database.execSQL(getMenuTableInitializationSql());

            // Create Menu Item Table
            database.execSQL(getMenuItemTableInitializationSql());
        }

        /**
         * Returns the SQL string for Menu Table creation
         * @return String
         */
        private String getMenuTableInitializationSql() {
            StringBuilder sqlCommand = new StringBuilder();
            sqlCommand.append("create table " + GlobalConstants.MENU_TABLE_NAME);
            sqlCommand.append(" (" + Storage.MENU_ROWID_COLUMN + " CHAR(16) PRIMARY KEY, " + Storage.MENU_LABEL_COLUMN + " TEXT NOT NULL);");
            return sqlCommand.toString();
        }

        /**
         * Returns the SQL string for MenuItem Table creation
         * @return String
         */
        private String getMenuItemTableInitializationSql() {

            StringBuilder sqlCommand = new StringBuilder();
            sqlCommand.append("create table " + GlobalConstants.MENU_ITEM_TABLE_NAME);
            sqlCommand.append(" (" + Storage.MENU_ITEM_ROWID_COLUMN + " CHAR(16) PRIMARY KEY, " + Storage.MENU_ITEM_LABEL_COLUMN + " TEXT NOT NULL, "
                                        + Storage.MENU_ITEM_MENUID_COLUMN + " CHAR(16), " + Storage.MENU_ITEM_PARENTID_COLUMN + " CHAR(16), "
                                        + Storage.MENU_ITEM_POSITION_COLUMN + " INTEGER, " + Storage.MENU_ITEM_CONTENT_COLUMN + " TEXT, ");
            sqlCommand.append(" FOREIGN KEY(menu_id) REFERENCES " + GlobalConstants.MENU_TABLE_NAME + "(id) ON DELETE CASCADE, ");
            sqlCommand.append(" FOREIGN KEY(parent_id) REFERENCES " + GlobalConstants.MENU_ITEM_TABLE_NAME + "(id) ON DELETE CASCADE, ");
            sqlCommand.append(" );");
            return sqlCommand.toString();
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            Log.w("StorageAdapter", "***Upgrading database from version*** "
                    + oldVersion + " to " + newVersion
                    + ", which will destroy all old data");

            // Get rid of old tables
            database.execSQL("DROP TABLE IF EXISTS keywords");
            database.execSQL("DROP TABLE IF EXISTS keywords2");

            // Get rid of new tables if they exist
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.MENU_TABLE_NAME);
            database.execSQL("DROP TABLE IF EXISTS " + GlobalConstants.MENU_ITEM_TABLE_NAME);

            onCreate(database);
        }
    }
}
