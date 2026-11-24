package com.pocketmanager.app

// XAF integers, never floating-point money. Each transfer is a single ledger event.
data class Account(val id:String,val name:String,val type:String,val space:String,val opening:Long)
data class Category(val id:String,val name:String,val kind:String)
data class Entry(val id:String,val kind:String,val account:String,val target:String?,val category:String?,val amount:Long,val fee:Long,val date:String,val note:String)
data class Wish(val id:String,val name:String,val price:Long,val reserved:Long,val priority:String)
data class Budget(val id:String,val category:String,val month:String,val limit:Long)
fun balances(accounts:List<Account>, entries:List<Entry>):Map<String,Long> {
    val out=accounts.associate { it.id to it.opening }.toMutableMap()
    fun change(id:String, amount:Long) { out[id]=Math.addExact(out.getValue(id),amount) }
    for(e in entries) when(e.kind) {
        "Entrée" -> change(e.account,e.amount)
        "Dépense" -> change(e.account,-e.amount)
        "Transfert" -> { change(e.account,-Math.addExact(e.amount,e.fee)); change(requireNotNull(e.target),e.amount) }
        else -> error("Type inconnu")
    }
    return out
}
fun income(entries:List<Entry>)=entries.filter{it.kind=="Entrée"}.fold(0L){a,e->Math.addExact(a,e.amount)}
fun expense(entries:List<Entry>)=entries.fold(0L){a,e->Math.addExact(a,when(e.kind){"Dépense"->e.amount;"Transfert"->e.fee;else->0L})}
fun validEntry(e:Entry,accounts:List<Account>,categories:List<Category>):Boolean = runCatching {
    require(e.id.isNotBlank() && e.amount in 1..999_999_999_999L && e.fee in 0..999_999_999_999L && accounts.any{it.id==e.account})
    require(java.time.LocalDate.parse(e.date).toString()==e.date)
    when(e.kind){
        "Transfert" -> require(e.target!=e.account && accounts.any{it.id==e.target} && e.category==null)
        "Entrée","Dépense" -> require(e.target==null && e.fee==0L && categories.any{it.id==e.category && it.kind==e.kind})
        else -> error("Type invalide")
    }; true
}.getOrDefault(false)
