package com.pocketmanager.app

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun uid()=UUID.randomUUID().toString()
class Store(context:Context):SQLiteOpenHelper(context,"pocket.db",null,1){
    override fun onCreate(db:SQLiteDatabase){
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, kind TEXT NOT NULL, body TEXT NOT NULL)")
        listOf("Alimentation","Transport","Logement","Santé","Internet","Loisirs","Famille","Autres").forEach{put(db,"category",uid(),JSONObject().put("name",it).put("type","Dépense"))}
        listOf("Salaire","Ventes","Services","Cadeaux","Autres").forEach{put(db,"category",uid(),JSONObject().put("name",it).put("type","Entrée"))}
        put(db,"account",uid(),JSONObject().put("name","Espèces").put("type","Espèces").put("space","Personnel").put("opening",0))
    }
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int){ error("Migration manquante") }
    private fun put(db:SQLiteDatabase,kind:String,id:String,body:JSONObject){
        val v=ContentValues().apply{put("id",id);put("kind",kind);put("body",body.toString())}
        check(db.insertWithOnConflict("records",null,v,SQLiteDatabase.CONFLICT_REPLACE)!=-1L)
    }
    fun put(kind:String,id:String,body:JSONObject)=put(writableDatabase,kind,id,body)
    fun remove(id:String){writableDatabase.delete("records","id=?",arrayOf(id))}
    private fun rows(kind:String):List<Pair<String,JSONObject>> = readableDatabase.query("records",arrayOf("id","body"),"kind=?",arrayOf(kind),null,null,"rowid").use{c->buildList{while(c.moveToNext())add(c.getString(0) to JSONObject(c.getString(1)))}}
    fun accounts()=rows("account").map{(id,j)->Account(id,j.getString("name"),j.getString("type"),j.getString("space"),j.getLong("opening"))}
    fun categories()=rows("category").map{(id,j)->Category(id,j.getString("name"),j.getString("type"))}
    fun entries()=rows("entry").map{(id,j)->Entry(id,j.getString("type"),j.getString("account"),j.optString("target").takeIf{it.isNotBlank()},j.optString("category").takeIf{it.isNotBlank()},j.getLong("amount"),j.getLong("fee"),j.getString("date"),j.getString("note"))}.sortedByDescending{it.date}
    fun wishes()=rows("wish").map{(id,j)->Wish(id,j.getString("name"),j.getLong("price"),j.getLong("reserved"),j.getString("priority"))}
    fun budgets()=rows("budget").map{(id,j)->Budget(id,j.getString("category"),j.getString("month"),j.getLong("limit"))}
    fun save(e:Entry){require(validEntry(e,accounts(),categories()));balances(accounts(),entries().filterNot{it.id==e.id}+e);put("entry",e.id,JSONObject().put("type",e.kind).put("account",e.account).put("target",e.target?:"").put("category",e.category?:"").put("amount",e.amount).put("fee",e.fee).put("date",e.date).put("note",e.note))}
    fun export():ByteArray {val a=JSONArray();readableDatabase.query("records",arrayOf("id","kind","body"),null,null,null,null,"rowid").use{while(it.moveToNext())a.put(JSONObject().put("id",it.getString(0)).put("kind",it.getString(1)).put("body",JSONObject(it.getString(2))))};return JSONObject().put("schema",1).put("records",a).toString().toByteArray(Charsets.UTF_8)}
    fun restore(bytes:ByteArray){
        val root=JSONObject(bytes.toString(Charsets.UTF_8));require(root.getInt("schema")==1);val a=root.getJSONArray("records");require(a.length()<=100000)
        val db=writableDatabase;db.beginTransaction();try{
            db.delete("records",null,null);val ids=mutableSetOf<String>()
            for(i in 0 until a.length()){val r=a.getJSONObject(i);val id=r.getString("id");val k=r.getString("kind");require(id.isNotBlank()&&ids.add(id)&&k in setOf("account","category","entry","wish","budget"));put(db,k,id,r.getJSONObject("body"))}
            val ac=accounts();val ca=categories();val en=entries();require(ac.isNotEmpty()&&ac.all{it.name.isNotBlank()&&it.space in listOf("Personnel","Professionnel")&&it.opening in -999_999_999_999L..999_999_999_999L});require(ca.all{it.name.isNotBlank()&&it.kind in listOf("Entrée","Dépense")});require(en.all{validEntry(it,ac,ca)});balances(ac,en)
            require(wishes().all{it.name.isNotBlank()&&it.price>0&&it.reserved in 0..it.price});require(budgets().all{b->b.limit>0&&ca.any{it.id==b.category&&it.kind=="Dépense"}&&java.time.YearMonth.parse(b.month).toString()==b.month});db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }
}
