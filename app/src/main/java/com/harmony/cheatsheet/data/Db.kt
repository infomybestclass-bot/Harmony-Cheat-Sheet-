package com.harmony.cheatsheet.data

import android.content.Context
import androidx.room.*
import org.json.JSONObject

@Entity(tableName = "scale") data class ScaleRow(
    @PrimaryKey val name: String, val kind: String, val grp: String, val sortOrder: Int,
    val degrees: String /* "C|Dm|Em|F|G|Am|B°" */)

@Entity(tableName = "chord_spelling", primaryKeys = ["tonic", "type"]) data class ChordSpelling(
    val tonic: String, val type: String, val notes: String, val typeOrder: Int)

@Entity(tableName = "dominant") data class Dominant(@PrimaryKey val tonic: String, val dominant: String)

@Entity(tableName = "relative") data class Relative(
    @PrimaryKey val tonic: String, val dominant: String, val subdominant: String,
    val relativeKey: String, val relDominant: String, val relSubdominant: String, val parallel: String)

@Entity(tableName = "chord_list") data class ChordListItem(@PrimaryKey val name: String, val sortOrder: Int)

@Dao interface HarmonyDao {
    @Query("SELECT * FROM scale ORDER BY sortOrder") suspend fun scales(): List<ScaleRow>
    @Query("SELECT * FROM chord_spelling WHERE tonic = :t ORDER BY typeOrder") suspend fun spellings(t: String): List<ChordSpelling>
    @Query("SELECT dominant FROM dominant WHERE tonic = :t") suspend fun dominantOf(t: String): String?
    @Query("SELECT * FROM relative WHERE tonic = :t") suspend fun relative(t: String): Relative?
    @Query("SELECT * FROM chord_list ORDER BY sortOrder") suspend fun chordList(): List<ChordListItem>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(a: List<ScaleRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putS(a: List<ChordSpelling>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putD(a: List<Dominant>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putR(a: List<Relative>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putC(a: List<ChordListItem>)
}

@Database(
    entities = [ScaleRow::class, ChordSpelling::class, Dominant::class, Relative::class, ChordListItem::class],
    version = 1, exportSchema = false)
abstract class HarmonyDb : RoomDatabase() {
    abstract fun dao(): HarmonyDao
    companion object {
        @Volatile private var inst: HarmonyDb? = null
        fun get(ctx: Context): HarmonyDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, HarmonyDb::class.java, "harmony.db").build().also { inst = it }
        }
    }
}

/** One-time seeding from assets/harmony.json (the data exported from the Excel file). */
suspend fun HarmonyDb.seedIfEmpty(ctx: Context) {
    val d = dao()
    if (d.chordList().isNotEmpty()) return
    val j = JSONObject(ctx.assets.open("harmony.json").bufferedReader().readText())
    val sc = j.getJSONArray("scales")
    d.put((0 until sc.length()).map { i -> sc.getJSONObject(i).let { o ->
        ScaleRow(o.getString("name"), o.getString("kind"), o.getString("group"), i,
            (0 until 7).joinToString("|") { o.getJSONArray("chords").getString(it) }) } })
    val types = j.getJSONArray("chordTypes")
    val cc = j.getJSONArray("chordChart")
    d.putS((0 until cc.length()).flatMap { i -> val o = cc.getJSONObject(i); val m = o.getJSONObject("types")
        (0 until types.length()).mapNotNull { k -> val t = types.getString(k)
            if (m.isNull(t)) null else ChordSpelling(o.getString("tonic"), t, m.getString(t), k) } })
    val dm = j.getJSONObject("dominant")
    d.putD(dm.keys().asSequence().map { Dominant(it, dm.getString(it)) }.toList())
    val r = j.getJSONArray("relative")
    d.putR((0 until r.length()).map { r.getJSONObject(it).let { o -> Relative(o.getString("tonic"), o.getString("dominant"),
        o.getString("subdominant"), o.getString("relativeKey"), o.getString("relDominant"),
        o.getString("relSubdominant"), o.getString("parallel")) } })
    val cl = j.getJSONArray("chordList")
    d.putC((0 until cl.length()).map { ChordListItem(cl.getString(it), it) })
}
