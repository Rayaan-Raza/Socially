package com.group.i230535_i230048 // Make sure this package is correct

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDbHelper(context: Context) :
    SQLiteOpenHelper(context, DB.DATABASE_NAME, null, DB.DATABASE_VERSION) {

    // SQL command to create the users table
    private val CREATE_USERS_TABLE = "CREATE TABLE " + DB.User.TABLE_NAME + "(" +
            DB.User.COLUMN_UID + " TEXT PRIMARY KEY," +
            DB.User.COLUMN_USERNAME + " TEXT," +
            DB.User.COLUMN_FULL_NAME + " TEXT," +
            DB.User.COLUMN_PROFILE_PIC_URL + " TEXT," +
            DB.User.COLUMN_EMAIL + " TEXT," +
            DB.User.COLUMN_BIO + " TEXT," +
            DB.User.COLUMN_IS_ONLINE + " INTEGER DEFAULT 0," +
            DB.User.COLUMN_LAST_SEEN + " INTEGER)"

    // SQL command to create the posts table
    private val CREATE_POSTS_TABLE = "CREATE TABLE " + DB.Post.TABLE_NAME + "(" +
            DB.Post.COLUMN_POST_ID + " TEXT PRIMARY KEY," +
            DB.Post.COLUMN_UID + " TEXT," +
            DB.Post.COLUMN_USERNAME + " TEXT," +
            DB.Post.COLUMN_CAPTION + " TEXT," +
            DB.Post.COLUMN_IMAGE_URL + " TEXT," +
            DB.Post.COLUMN_IMAGE_BASE64 + " TEXT," +
            DB.Post.COLUMN_CREATED_AT + " INTEGER," +
            DB.Post.COLUMN_LIKE_COUNT + " INTEGER," +
            DB.Post.COLUMN_COMMENT_COUNT + " INTEGER)"

    // SQL command to create the comments table
    private val CREATE_COMMENTS_TABLE = "CREATE TABLE " + DB.Comment.TABLE_NAME + "(" +
            DB.Comment.COLUMN_COMMENT_ID + " TEXT PRIMARY KEY," +
            DB.Comment.COLUMN_POST_ID + " TEXT," +
            DB.Comment.COLUMN_UID + " TEXT," +
            DB.Comment.COLUMN_USERNAME + " TEXT," +
            DB.Comment.COLUMN_TEXT + " TEXT," +
            DB.Comment.COLUMN_CREATED_AT + " INTEGER)"

    // SQL command to create the messages table
    private val CREATE_MESSAGES_TABLE = "CREATE TABLE " + DB.Message.TABLE_NAME + "(" +
            DB.Message.COLUMN_MESSAGE_ID + " TEXT PRIMARY KEY," +
            DB.Message.COLUMN_SENDER_ID + " TEXT," +
            DB.Message.COLUMN_RECEIVER_ID + " TEXT," +
            DB.Message.COLUMN_MESSAGE_TYPE + " TEXT," +
            DB.Message.COLUMN_CONTENT + " TEXT," +
            DB.Message.COLUMN_IMAGE_URL + " TEXT," +
            DB.Message.COLUMN_POST_ID + " TEXT," +
            DB.Message.COLUMN_TIMESTAMP + " INTEGER," +
            DB.Message.COLUMN_IS_EDITED + " INTEGER DEFAULT 0," +
            DB.Message.COLUMN_IS_DELETED + " INTEGER DEFAULT 0)"

    private val CREATE_CHAT_SESSIONS_TABLE = "CREATE TABLE " + DB.ChatSessionInfo.TABLE_NAME + "(" +
            DB.ChatSessionInfo.COLUMN_CHAT_ID + " TEXT PRIMARY KEY," +
            DB.ChatSessionInfo.COLUMN_OTHER_USER_ID + " TEXT," +
            DB.ChatSessionInfo.COLUMN_OTHER_USERNAME + " TEXT," +
            DB.ChatSessionInfo.COLUMN_OTHER_PIC_URL + " TEXT," +
            DB.ChatSessionInfo.COLUMN_LAST_MESSAGE + " TEXT," +
            DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_TIMESTAMP + " INTEGER," +
            DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_SENDER_ID + " TEXT)"
    // SQL command to create the stories table
    private val CREATE_STORIES_TABLE = "CREATE TABLE " + DB.Story.TABLE_NAME + "(" +
            DB.Story.COLUMN_STORY_ID + " TEXT PRIMARY KEY," +
            DB.Story.COLUMN_UID + " TEXT," +
            DB.Story.COLUMN_MEDIA_URL + " TEXT," +
            DB.Story.COLUMN_MEDIA_TYPE + " TEXT," +
            DB.Story.COLUMN_CREATED_AT + " INTEGER," +
            DB.Story.COLUMN_EXPIRES_AT + " INTEGER)"

    // SQL command to create the offline sync queue
    private val CREATE_SYNC_QUEUE_TABLE = "CREATE TABLE " + DB.SyncQueue.TABLE_NAME + "(" +
            DB.SyncQueue.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            DB.SyncQueue.COLUMN_ENDPOINT + " TEXT NOT NULL," +
            DB.SyncQueue.COLUMN_PAYLOAD + " TEXT NOT NULL," +
            DB.SyncQueue.COLUMN_STATUS + " TEXT NOT NULL)"


    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(CREATE_USERS_TABLE)
        db?.execSQL(CREATE_POSTS_TABLE)
        db?.execSQL(CREATE_COMMENTS_TABLE)
        db?.execSQL(CREATE_MESSAGES_TABLE)
        db?.execSQL(CREATE_STORIES_TABLE)
        db?.execSQL(CREATE_SYNC_QUEUE_TABLE)
        db?.execSQL(CREATE_CHAT_SESSIONS_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS " + DB.User.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + DB.Post.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + DB.Comment.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + DB.Message.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + DB.Story.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + DB.SyncQueue.TABLE_NAME)
        db?.execSQL("DROP TABLE IF EXISTS " + DB.ChatSessionInfo.TABLE_NAME)
        onCreate(db)
    }
}