package com.pocketmanager.app

// XAF integers, never floating-point money. Each transfer is a single ledger event.
data class Account(val id:String,val name:String,val type:String,val space:String,val opening:Long)
data class Category(val id:String,val name:String,val kind:String)
data class Entry(val id:String,val kind:String,val account:String,val target:String?,val category:String?,val amount:Long,val fee:Long,val date:String,val note:String,val time:String="")
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
    require(java.time.LocalDate.parse(e.date).toString()==e.date && validTime(e.time))
    when(e.kind){
        "Transfert" -> require(e.target!=e.account && accounts.any{it.id==e.target} && e.category==null)
        "Entrée","Dépense" -> require(e.target==null && e.fee==0L && categories.any{it.id==e.category && it.kind==e.kind})
        else -> error("Type invalide")
    }; true
}.getOrDefault(false)

fun validTime(t:String)=t.isEmpty() || (t.matches(Regex("[0-9]{2}:[0-9]{2}")) && runCatching{java.time.LocalTime.parse(t)}.isSuccess)
data class Payment(val id:String,val account:String,val amount:Long,val date:String,val time:String)
data class Obligation(val id:String,val kind:String,val person:String,val amount:Long,val date:String,val time:String,val due:String,val space:String,val originAccount:String?,val note:String,val payments:List<Payment>){
    val paid:Long get()=payments.fold(0L){a,p->Math.addExact(a,p.amount)}
    val remaining:Long get()=amount-paid
}
fun validObligation(d:Obligation,ac:List<Account>):Boolean=runCatching{
    require(d.id.isNotBlank()&&d.kind in listOf("Dette","Créance")&&d.person.isNotBlank()&&d.amount in 1..999_999_999_999L&&d.space in listOf("Personnel","Professionnel"))
    require(java.time.LocalDate.parse(d.date).toString()==d.date&&validTime(d.time))
    require(d.due.isEmpty()||java.time.LocalDate.parse(d.due).toString()==d.due)
    require(d.originAccount==null||ac.any{it.id==d.originAccount&&it.space==d.space})
    require(d.payments.map{it.id}.distinct().size==d.payments.size&&d.payments.all{p->p.id.isNotBlank()&&p.amount>0&&ac.any{it.id==p.account&&it.space==d.space}&&java.time.LocalDate.parse(p.date).toString()==p.date&&p.date>=d.date&&validTime(p.time)})
    require(d.paid<=d.amount);true
}.getOrDefault(false)
fun financialBalances(ac:List<Account>,en:List<Entry>,debts:List<Obligation>):Map<String,Long>{
    val out=balances(ac,en).toMutableMap()
    fun add(id:String,v:Long){out[id]=Math.addExact(out.getValue(id),v)}
    for(d in debts){val sign=if(d.kind=="Dette")1L else -1L;d.originAccount?.let{add(it,sign*d.amount)};d.payments.forEach{add(it.account,-sign*it.amount)}}
    return out
}
