package org.openexposuretrace.oextrace.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import org.openexposuretrace.oextrace.data.LogTableValue


@Database(entities = [LogTableValue::class], version = 1)

@TypeConverters(DatabaseConverters::class)
abstract class Database : RoomDatabase() {

    abstract fun appDao(): OextraceDao

}
