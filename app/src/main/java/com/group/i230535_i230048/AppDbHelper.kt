// ==================== AppDbHelper.kt ====================
package com.group.i230535_i230048

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class AppDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DB.DATABASE_NAME,
    null,
    DB.DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        Log.d("AppDbHelper", "Creating database tables...")

        // Create Users table WITH photo column
        db.execSQL(
            """
            CREATE TABLE ${DB.User.TABLE_NAME} (
                ${DB.User.COLUMN_UID} TEXT PRIMARY KEY,
                ${DB.User.COLUMN_USERNAME} TEXT,
                ${DB.User.COLUMN_FULL_NAME} TEXT,
                ${DB.User.COLUMN_PROFILE_PIC_URL} TEXT,
                ${DB.User.COLUMN_PHOTO} TEXT,
                ${DB.User.COLUMN_EMAIL} TEXT,
                ${DB.User.COLUMN_BIO} TEXT,
                ${DB.User.COLUMN_IS_ONLINE} INTEGER DEFAULT 0,
                ${DB.User.COLUMN_LAST_SEEN} INTEGER DEFAULT 0
            )
        """
        )

        // Create Posts table
        db.execSQL(
            """
            CREATE TABLE ${DB.Post.TABLE_NAME} (
                ${DB.Post.COLUMN_POST_ID} TEXT PRIMARY KEY,
                ${DB.Post.COLUMN_UID} TEXT,
                ${DB.Post.COLUMN_USERNAME} TEXT,
                ${DB.Post.COLUMN_CAPTION} TEXT,
                ${DB.Post.COLUMN_IMAGE_URL} TEXT,
                ${DB.Post.COLUMN_IMAGE_BASE64} TEXT,
                ${DB.Post.COLUMN_CREATED_AT} INTEGER,
                ${DB.Post.COLUMN_LIKE_COUNT} INTEGER DEFAULT 0,
                ${DB.Post.COLUMN_COMMENT_COUNT} INTEGER DEFAULT 0,
                ${DB.Post.COLUMN_I_LIKED} INTEGER DEFAULT 0
            )
        """
        )

        // Create Comments table
        db.execSQL(
            """
            CREATE TABLE ${DB.Comment.TABLE_NAME} (
                ${DB.Comment.COLUMN_COMMENT_ID} TEXT PRIMARY KEY,
                ${DB.Comment.COLUMN_POST_ID} TEXT,
                ${DB.Comment.COLUMN_UID} TEXT,
                ${DB.Comment.COLUMN_USERNAME} TEXT,
                ${DB.Comment.COLUMN_TEXT} TEXT,
                ${DB.Comment.COLUMN_CREATED_AT} INTEGER,
                FOREIGN KEY(${DB.Comment.COLUMN_POST_ID}) 
                    REFERENCES ${DB.Post.TABLE_NAME}(${DB.Post.COLUMN_POST_ID})
                    ON DELETE CASCADE
            )
        """
        )

        // Create Messages table
        db.execSQL(
            """
            CREATE TABLE ${DB.Message.TABLE_NAME} (
                ${DB.Message.COLUMN_MESSAGE_ID} TEXT PRIMARY KEY,
                ${DB.Message.COLUMN_SENDER_ID} TEXT,
                ${DB.Message.COLUMN_RECEIVER_ID} TEXT,
                ${DB.Message.COLUMN_MESSAGE_TYPE} TEXT,
                ${DB.Message.COLUMN_CONTENT} TEXT,
                ${DB.Message.COLUMN_IMAGE_URL} TEXT,
                ${DB.Message.COLUMN_POST_ID} TEXT,
                ${DB.Message.COLUMN_TIMESTAMP} INTEGER,
                ${DB.Message.COLUMN_IS_EDITED} INTEGER DEFAULT 0,
                ${DB.Message.COLUMN_IS_DELETED} INTEGER DEFAULT 0
            )
        """
        )

        // Create Stories table
        db.execSQL(
            """
            CREATE TABLE ${DB.Story.TABLE_NAME} (
                ${DB.Story.COLUMN_STORY_ID} TEXT PRIMARY KEY,
                ${DB.Story.COLUMN_UID} TEXT,
                ${DB.Story.COLUMN_MEDIA_URL} TEXT,
                ${DB.Story.COLUMN_MEDIA_TYPE} TEXT,
                ${DB.Story.COLUMN_CREATED_AT} INTEGER,
                ${DB.Story.COLUMN_EXPIRES_AT} INTEGER
            )
        """
        )

        // Create Story Bubbles table
        db.execSQL(
            """
            CREATE TABLE ${DB.StoryBubble.TABLE_NAME} (
                ${DB.StoryBubble.COLUMN_UID} TEXT PRIMARY KEY,
                ${DB.StoryBubble.COLUMN_USERNAME} TEXT,
                ${DB.StoryBubble.COLUMN_PROFILE_URL} TEXT,
                ${DB.StoryBubble.COLUMN_HAS_UNSEEN} INTEGER DEFAULT 0,
                ${DB.StoryBubble.COLUMN_LATEST_STORY_TIMESTAMP} INTEGER DEFAULT 0
            )
        """
        )

        // Create Chat Sessions table
        db.execSQL(
            """
            CREATE TABLE ${DB.ChatSessionInfo.TABLE_NAME} (
                ${DB.ChatSessionInfo.COLUMN_CHAT_ID} TEXT PRIMARY KEY,
                ${DB.ChatSessionInfo.COLUMN_OTHER_USER_ID} TEXT,
                ${DB.ChatSessionInfo.COLUMN_OTHER_USERNAME} TEXT,
                ${DB.ChatSessionInfo.COLUMN_OTHER_PIC_URL} TEXT,
                ${DB.ChatSessionInfo.COLUMN_LAST_MESSAGE} TEXT,
                ${DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_TIMESTAMP} INTEGER,
                ${DB.ChatSessionInfo.COLUMN_LAST_MESSAGE_SENDER_ID} TEXT
            )
        """
        )

        // Create Sync Queue table
        db.execSQL(
            """
            CREATE TABLE ${DB.SyncQueue.TABLE_NAME} (
                ${DB.SyncQueue.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${DB.SyncQueue.COLUMN_ENDPOINT} TEXT,
                ${DB.SyncQueue.COLUMN_PAYLOAD} TEXT,
                ${DB.SyncQueue.COLUMN_STATUS} TEXT,
                ${DB.SyncQueue.COLUMN_CREATED_AT} INTEGER DEFAULT (strftime('%s','now') * 1000)
            )
        """
        )

        // Create indexes for better performance
        db.execSQL("CREATE INDEX idx_posts_uid ON ${DB.Post.TABLE_NAME}(${DB.Post.COLUMN_UID})")
        db.execSQL("CREATE INDEX idx_posts_created ON ${DB.Post.TABLE_NAME}(${DB.Post.COLUMN_CREATED_AT} DESC)")
        db.execSQL("CREATE INDEX idx_comments_post ON ${DB.Comment.TABLE_NAME}(${DB.Comment.COLUMN_POST_ID})")
        db.execSQL("CREATE INDEX idx_messages_sender ON ${DB.Message.TABLE_NAME}(${DB.Message.COLUMN_SENDER_ID})")
        db.execSQL("CREATE INDEX idx_messages_receiver ON ${DB.Message.TABLE_NAME}(${DB.Message.COLUMN_RECEIVER_ID})")
        db.execSQL("CREATE INDEX idx_messages_timestamp ON ${DB.Message.TABLE_NAME}(${DB.Message.COLUMN_TIMESTAMP} DESC)")
        db.execSQL("CREATE INDEX idx_stories_uid ON ${DB.Story.TABLE_NAME}(${DB.Story.COLUMN_UID})")
        db.execSQL("CREATE INDEX idx_stories_expires ON ${DB.Story.TABLE_NAME}(${DB.Story.COLUMN_EXPIRES_AT})")
        db.execSQL("CREATE INDEX idx_sync_status ON ${DB.SyncQueue.TABLE_NAME}(${DB.SyncQueue.COLUMN_STATUS})")

        Log.d("AppDbHelper", "Database tables created successfully")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.d("AppDbHelper", "Upgrading database from version $oldVersion to $newVersion")

        when {
            oldVersion < 2 -> {
                // Upgrade from version 1 to 2: Add story_bubbles table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ${DB.StoryBubble.TABLE_NAME} (
                        ${DB.StoryBubble.COLUMN_UID} TEXT PRIMARY KEY,
                        ${DB.StoryBubble.COLUMN_USERNAME} TEXT,
                        ${DB.StoryBubble.COLUMN_PROFILE_URL} TEXT,
                        ${DB.StoryBubble.COLUMN_HAS_UNSEEN} INTEGER DEFAULT 0,
                        ${DB.StoryBubble.COLUMN_LATEST_STORY_TIMESTAMP} INTEGER DEFAULT 0
                    )
                """
                )

                // Add iLiked column to posts table if it doesn't exist
                try {
                    db.execSQL(
                        """
                        ALTER TABLE ${DB.Post.TABLE_NAME} 
                        ADD COLUMN ${DB.Post.COLUMN_I_LIKED} INTEGER DEFAULT 0
                    """
                    )
                } catch (e: Exception) {
                    Log.w("AppDbHelper", "Column iLiked might already exist: ${e.message}")
                }

                // Add createdAt column to sync_queue if it doesn't exist
                try {
                    db.execSQL(
                        """
                        ALTER TABLE ${DB.SyncQueue.TABLE_NAME} 
                        ADD COLUMN ${DB.SyncQueue.COLUMN_CREATED_AT} INTEGER DEFAULT (strftime('%s','now') * 1000)
                    """
                    )
                } catch (e: Exception) {
                    Log.w("AppDbHelper", "Column createdAt might already exist: ${e.message}")
                }

                Log.d("AppDbHelper", "Upgraded to version 2: Added story_bubbles table")
            }
        }

        // NEW: Upgrade to version 3 - Add photo column
        if (oldVersion < 3) {
            Log.d("AppDbHelper", "Upgrading to version 3: Adding photo column")
            try {
                db.execSQL(
                    """
                    ALTER TABLE ${DB.User.TABLE_NAME} 
                    ADD COLUMN ${DB.User.COLUMN_PHOTO} TEXT
                """
                )
                Log.d("AppDbHelper", "Successfully added photo column to users table")
            } catch (e: Exception) {
                Log.w("AppDbHelper", "Column photo might already exist: ${e.message}")
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Enable foreign key constraints
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDbHelper? = null

        fun getInstance(context: Context): AppDbHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDbHelper(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}