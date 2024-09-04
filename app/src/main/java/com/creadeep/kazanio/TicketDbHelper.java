package com.creadeep.kazanio;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class TicketDbHelper extends SQLiteOpenHelper {

  private static TicketDbHelper mInstance = null;
  private static final String DATABASE_NAME = "Kazanio.db";
  private static final String TABLE_NAME = "TicketData";
  private static final String COL_1 = "Id";
  private static final String COL_2 = "GameType";
  private static final String COL_3 = "TicketType";
  private static final String COL_4 = "Number";
  private static final String COL_5 = "Date";
  private static final String COL_6 = "Fraction";
  private static final String COL_7 = "Prize";
  private static final String COL_8 = "Notify"; // If notification is active for future draw
  private static final String COL_9 = "Drawn"; // If lottery is drawn yet
  private static final String COL_10 = "Tease"; // If result will be shown in recycler view
  private static final String INDEX = "index_TicketData_GameType_Number_Date"; // This is taken from the schema mismatch exception (Room gives this name)
  private static final int DATABASE_VERSION = 4;

  private TicketDbHelper(Context context) {
    super(context, DATABASE_NAME, null, DATABASE_VERSION);
  }

  // https://stackoverflow.com/questions/18147354/sqlite-connection-leaked-although-everything-closed/18148718#18148718
  public static TicketDbHelper getInstance(Context ctx) {
    // Use the application context, which will ensure that you
    // don't accidentally leak an Activity's context.
    // See this article for more information: http://bit.ly/6LRzfx
    if (mInstance == null) {
      mInstance = new TicketDbHelper(ctx.getApplicationContext());
    }
    return mInstance;
  }

  @Override
  public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE " + TABLE_NAME +" (" +
            COL_1 + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            COL_2 + " TEXT," +
            COL_3 + " TEXT," +
            COL_4 + " TEXT," +
            COL_5 + " TEXT," +
            COL_6 + " TEXT," +
            COL_7 + " TEXT," +
            COL_8 + " INTEGER," +
            COL_9 + " INTEGER," +
            COL_10 + " INTEGER" + ")");
    db.execSQL("CREATE UNIQUE INDEX " + INDEX + " ON " + TABLE_NAME + "(" + COL_2 + ", " + COL_4 + ", " + COL_5 + ")"); // There can be only single entry with the same type, number & date. In case of conflict during insertion, new value replaces the old one
  }

  @Override
  public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
    onCreate(db);
  }

  public boolean insertData(int type, int ticketType, String number, String date, String fraction, String prize, int notify, int drawn, int tease) { // updated in case of conflict
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues contentValues = new ContentValues();
    contentValues.put(COL_2, type);
    contentValues.put(COL_3, ticketType);
    contentValues.put(COL_4, number);
    contentValues.put(COL_5, date);
    contentValues.put(COL_6, fraction);
    contentValues.put(COL_7, prize);
    contentValues.put(COL_8, notify);
    contentValues.put(COL_9, drawn);
    contentValues.put(COL_10, tease);
    long result = db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    return result != -1;
  }

  /**
   * Used to check if given data exists in the db
   * @param number number of the ticket to be checked
   * @param date date of the ticket to be checked
   * @return present ticket cursor
   */
  public Cursor checkPresence(String type, String number, String date){
    SQLiteDatabase db = this.getWritableDatabase();
    String query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COL_2 + "=\"" + type + "\" AND " + COL_4 + "=\"" + number + "\" AND " + COL_5 + "=\"" + date + "\"";
    return db.rawQuery(query, null);
  }

  /**
   * Returns if given data exists in the db
   * @param type game type
   * @param date date on the ticket
   * @return true: exists, false: doesn't
   */
  public boolean isPresent(String type, String date){
    SQLiteDatabase db = this.getWritableDatabase();
    String query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COL_2 + "=\"" + type + "\" AND " + COL_5 + "=\"" + date + "\"";
    Cursor cursor = db.rawQuery(query, null);
    if (cursor.moveToNext()) {
      cursor.close();
      return true;
    }
    else {
      cursor.close();
      return false;
    }
  }

  public Cursor getAllTickets(){
    SQLiteDatabase db = this.getWritableDatabase();
    String query = "SELECT * FROM " + TABLE_NAME + " ORDER BY " + COL_9 + " ASC" + ", " + COL_1 + " DESC";
    return db.rawQuery(query, null);
  }

  public Cursor getAllTicketsOfType(int type){
    SQLiteDatabase db = this.getWritableDatabase();
    String query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COL_2 + "=" + type + " ORDER BY " + COL_9 + " ASC" + ", " + COL_1 + " DESC";
    return db.rawQuery(query, null);
  }

  public Ticket fetchTicketFromDb(int type, String number, String date){
    SQLiteDatabase db = this.getWritableDatabase();
    String query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COL_2 + "=" + type + " AND " + COL_4 + "=\"" + number + "\" AND " + COL_5 + "=\"" + date + "\"";
    Cursor ticketCursor = db.rawQuery(query, null);
    if (ticketCursor.moveToFirst()) {
      Ticket ticketToReturn = new Ticket(
              ticketCursor.getInt(ticketCursor.getColumnIndex("GameType")),
              ticketCursor.getInt(ticketCursor.getColumnIndex("TicketType")),
              ticketCursor.getString(ticketCursor.getColumnIndex("Number")),
              ticketCursor.getString(ticketCursor.getColumnIndex("Date")),
              ticketCursor.getString(ticketCursor.getColumnIndex("Fraction")),
              ticketCursor.getString(ticketCursor.getColumnIndex("Prize")),
              ticketCursor.getInt(ticketCursor.getColumnIndex("Notify")),
              ticketCursor.getInt(ticketCursor.getColumnIndex("Drawn")),
              ticketCursor.getInt(ticketCursor.getColumnIndex("Tease")));
      ticketCursor.close();
      return(ticketToReturn);
    }
    ticketCursor.close();
    return null;
  }

  public void deleteTicketFromDb(String number, String date){ // TODO: add ticket type filter
    SQLiteDatabase db = this.getWritableDatabase();
    db.delete(TABLE_NAME, COL_4 + "=\"" + number + "\" AND " + COL_5 + "=\"" + date + "\"", null);
  }

  /**
   * Sets auto-checker status
   * @param number number of the ticket to be checked
   * @param date date of the ticket to be checked
   * @param value value to be set in auto checker
   */
  public void setAutoCheckerStatus(String number, String date, String value){
    SQLiteDatabase db = this.getWritableDatabase();
    db.execSQL("UPDate " + TABLE_NAME + " SET " + COL_8 + "=\"" + value + "\" WHERE " + COL_4 + "=\"" + number + "\" AND " + COL_5 + "=\"" + date + "\"");
  }

  public Cursor getAllUndrawnTickets(){
    SQLiteDatabase db = this.getWritableDatabase();
    String query = "SELECT * FROM " + TABLE_NAME + " WHERE " + COL_7 + " IS NULL AND " + COL_8 + "=1";
    return db.rawQuery(query, null);
  }

  public void cancelTease(Ticket ticket){
    SQLiteDatabase db = this.getWritableDatabase();
    db.execSQL("UPDate " + TABLE_NAME + " SET " + COL_10 + "=0 " + " WHERE " + COL_2 + "=\"" + ticket.getGameType() + "\" AND " + COL_4 + "=\"" + ticket.getNumber() + "\" AND " + COL_5 + "=\"" + ticket.getDate() + "\"");
  }
}
