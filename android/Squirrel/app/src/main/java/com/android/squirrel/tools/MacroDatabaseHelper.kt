package com.android.squirrel.tools
//数据库操作
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class MacroDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "macro_database.db"
        private const val DATABASE_VERSION = 1
    }

    override fun onCreate(db: SQLiteDatabase) {
        // 创建默认表
        val createTableQuery = """
            CREATE TABLE IF NOT EXISTS default_table (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                data BLOB NOT NULL,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            );
        """
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 升级时删除旧表并重新创建
        db.execSQL("DROP TABLE IF EXISTS default_table")
        onCreate(db)
    }

    // 创建新表：首先检查是否已存在该表，如果存在则不创建
    fun createNewTable(db: SQLiteDatabase, tableName: String): Boolean {
        val checkTableQuery = "SELECT name FROM sqlite_master WHERE type='table' AND name=?"
        val cursor: Cursor = db.rawQuery(checkTableQuery, arrayOf(tableName))

        return if (cursor.count == 0) {
            // 如果表不存在，则创建新表
            val createTableQuery = """
                CREATE TABLE IF NOT EXISTS $tableName (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    data BLOB NOT NULL,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                );
            """
            db.execSQL(createTableQuery)
            Log.d("Database", "Table $tableName created.")
            cursor.close()
            true
        } else {
            Log.d("Database", "Table $tableName already exists.")
            cursor.close()
            false
        }
    }

    // 插入数据到指定表，插入时间戳（由外部传入）
    fun insertData(db: SQLiteDatabase, tableName: String, byteArray: ByteArray, timestamp: Long): Boolean {
        val contentValues = ContentValues().apply {
            put("data", byteArray)
            put("timestamp", timestamp)
        }
        val result = db.insert(tableName, null, contentValues)
        return result != -1L  // 返回是否插入成功
    }

    // 删除指定表
    fun deleteTable(db: SQLiteDatabase, tableName: String): Boolean {
        return try {
            val deleteTableQuery = "DROP TABLE IF EXISTS $tableName"
            db.execSQL(deleteTableQuery)
            Log.d("Database", "Table $tableName deleted.")
            true
        } catch (e: Exception) {
            Log.e("Database Error", "Failed to delete table $tableName: ${e.message}")
            false
        }
    }

    // 查询所有表名
    fun getAllTableNames(db: SQLiteDatabase): List<String> {
        val cursor: Cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table';", null)
        val tableNames = mutableListOf<String>()

        // 获取列索引并进行检查
        val nameColumnIndex = cursor.getColumnIndex("name")
        if (nameColumnIndex == -1) {
            // 如果没有找到 "name" 列，可以进行调试输出
            Log.e("Database", "Column 'name' not found in sqlite_master")
        }

        if (cursor.moveToFirst()) {
            do {
                // 使用 getString 获取值
                val tableName = cursor.getString(nameColumnIndex)
                tableNames.add(tableName)
            } while (cursor.moveToNext())
        }

        cursor.close()
        return tableNames
    }


    // 获取指定表的数据，包括时间戳
    fun getData(db: SQLiteDatabase, tableName: String): List<Triple<ByteArray, String, Long>> {
        val query = "SELECT data, timestamp FROM $tableName"
        val cursor: Cursor = db.rawQuery(query, null)

        val dataList = mutableListOf<Triple<ByteArray, String, Long>>()

        val dataColumnIndex = cursor.getColumnIndex("data")
        val timestampColumnIndex = cursor.getColumnIndex("timestamp")

        if (dataColumnIndex != -1 && timestampColumnIndex != -1) {
            if (cursor.moveToFirst()) {
                do {
                    val byteArray = cursor.getBlob(dataColumnIndex)
                    val timestampStr = cursor.getString(timestampColumnIndex)
                    val timestamp = cursor.getLong(timestampColumnIndex)
                    dataList.add(Triple(byteArray, timestampStr, timestamp))
                } while (cursor.moveToNext())
            }
        } else {
            Log.e("Database Error", "Columns 'data' or 'timestamp' not found.")
        }

        cursor.close()
        return dataList
    }
}
