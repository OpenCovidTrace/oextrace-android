package org.openexposuretrace.oextrace.di


import androidx.room.Room
import org.openexposuretrace.oextrace.db.Database

object DatabaseProvider : IndependentProvider<Database>() {

    override fun initInstance(): Database {
        val context by ContextProvider()
        return Room.databaseBuilder(context, Database::class.java, "oextrace.db")
            .fallbackToDestructiveMigration()
            .build()
    }

}