package org.openexposuretrace.oextrace.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.openexposuretrace.oextrace.data.LogTableValue

@Dao
interface OextraceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertLog(logTableValue: LogTableValue): Long

    @Query("SELECT * from log_table ORDER BY time DESC")
    fun getLogsLiveData(): LiveData<List<LogTableValue>>

    @Query("DELETE FROM log_table")
    fun clearLogs()

}
