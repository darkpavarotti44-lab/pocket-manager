package com.pocketmanager.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

@Composable internal fun TimeFields(date:String,onDate:(String)->Unit,time:String,onTime:(String)->Unit){
    val context=LocalContext.current
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        OutlinedButton(modifier=Modifier.weight(1f),onClick={
            val d=runCatching{LocalDate.parse(date)}.getOrDefault(LocalDate.now())
            DatePickerDialog(context,{_,y,m,day->onDate(LocalDate.of(y,m+1,day).toString())},d.year,d.monthValue-1,d.dayOfMonth).show()
        }){Text(date.ifBlank{"Date"})}
        OutlinedButton(modifier=Modifier.weight(1f),onClick={
            val t=runCatching{LocalTime.parse(time)}.getOrDefault(LocalTime.now())
            TimePickerDialog(context,{_,h,m->onTime(String.format(java.util.Locale.ROOT,"%02d:%02d",h,m))},t.hour,t.minute,true).show()
        }){Text(time.ifBlank{"Heure inconnue"})}
    }
    TextButton(onClick={onDate(LocalDate.now().toString());onTime(nowTime())}){Text("Maintenant")}
}
private fun status(d:Obligation)=when{
    d.remaining==0L->"Soldé"
    d.due.isNotBlank()&&d.due<LocalDate.now().toString()->"En retard"
    d.paid>0->"Partiellement remboursé"
    else->"En cours"
}
private fun delta(d:Obligation?):Map<String,Long>{
    val out=mutableMapOf<String,Long>()
    if(d!=null){val sign=if(d.kind=="Dette")1L else -1L;fun add(id:String,v:Long){out[id]=(out[id]?:0L)+v};d.originAccount?.let{add(it,sign*d.amount)};d.payments.forEach{add(it.account,-sign*it.amount)}}
    return out
}
@Composable internal fun DebtScreen(debts:List<Obligation>,ac:List<Account>,scope:String,hidden:Boolean,p:Palette,balances:Map<String,Long>,policy:String,start:String,consume:()->Unit,onSave:(Obligation)->Boolean,onDelete:(Obligation)->Unit){
    var form by remember{mutableStateOf<Obligation?>(null)}
    var editing by remember{mutableStateOf(false)}
    var paymentFor by remember{mutableStateOf<Obligation?>(null)}
    var paymentOld by remember{mutableStateOf<Payment?>(null)}
    var deletion by remember{mutableStateOf<Obligation?>(null)}
    var paymentDeletion by remember{mutableStateOf<Pair<Obligation,Payment>?>(null)}
    var warning by remember{mutableStateOf("")}
    var pending by remember{mutableStateOf<Obligation?>(null)}
    var filter by remember{mutableStateOf("Tous")}
    var search by remember{mutableStateOf("")}
    fun display(v:Long)=if(hidden)"••••••" else money(v)
    fun fresh(kind:String)=Obligation(uid(),kind,"",0,LocalDate.now().toString(),nowTime(),"",if(scope=="Professionnel")scope else "Personnel",null,"",emptyList())
    fun submit(d:Obligation):Boolean{
        if(!validObligation(d,ac)){warning="Vérifie les montants, les dates et les comptes.";return false}
        val old=delta(debts.find{it.id==d.id});val next=delta(d)
        val insufficient=ac.any{a->val change=(next[a.id]?:0L)-(old[a.id]?:0L);change<0L&&(balances[a.id]?:0L)+change<0L}
        if(insufficient&&policy=="Bloquer"){warning="Solde insuffisant sur un compte.";return false}
        if(insufficient&&policy=="Avertir"){pending=d;return false}
        return onSave(d)
    }
    LaunchedEffect(start){if(start.isNotBlank()){form=fresh(start);editing=true;consume()}}
    Panel{Text("Mes dettes",fontWeight=FontWeight.Bold);Text("−"+display(debts.filter{it.kind=="Dette"}.sumOf{it.remaining}),color=p.negative);Text("Mes créances",fontWeight=FontWeight.Bold);Text("+"+display(debts.filter{it.kind=="Créance"}.sumOf{it.remaining}),color=p.positive)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={form=fresh("Dette");editing=true},modifier=Modifier.weight(1f)){Text("+ Dette")};OutlinedButton(onClick={form=fresh("Créance");editing=true},modifier=Modifier.weight(1f)){Text("+ Créance")}}
    Choice("Afficher",filter,listOf("Tous","Dette","Créance","En retard","Soldé")){filter=it}
    OutlinedTextField(search,{search=it},label={Text("Rechercher une personne")},singleLine=true,modifier=Modifier.fillMaxWidth())
    val shown=debts.filter{(filter=="Tous"||it.kind==filter||status(it)==filter)&&(it.person.contains(search,true)||it.note.contains(search,true))}
    if(shown.isEmpty())Text("Aucune dette ou créance dans cette sélection.")
    shown.forEach{d->
        Panel{
            Text(d.kind+" · "+d.person,fontWeight=FontWeight.Bold)
            Text((if(d.kind=="Dette")"−" else "+")+display(d.remaining),color=if(d.kind=="Dette")p.negative else p.positive,style=MaterialTheme.typography.titleLarge)
            Text("Initial : "+display(d.amount)+" · Remboursé : "+display(d.paid))
            Text(d.date+" à "+d.time.ifBlank{"heure non renseignée"}+" · "+d.space)
            Text(status(d)+(if(d.due.isBlank())"" else " · Échéance : "+d.due))
            Text(if(d.originAccount==null)"Somme antérieure / sans mouvement initial" else "Mouvement initial : "+(ac.find{it.id==d.originAccount}?.name?:"Compte"))
            if(d.note.isNotBlank())Text(d.note)
            if(!hidden)LinearProgressIndicator(progress={d.paid.toFloat()/d.amount},modifier=Modifier.fillMaxWidth())
            if(d.remaining>0)Button(onClick={paymentFor=d;paymentOld=null}){Text(if(d.kind=="Dette")"Rembourser" else "Encaisser un remboursement")}
            Row{TextButton(onClick={form=d;editing=true}){Text("Modifier")};TextButton(onClick={deletion=d}){Text("Supprimer",color=p.negative)}}
            if(d.payments.isNotEmpty()){
                Text("Historique des remboursements",fontWeight=FontWeight.Bold)
                d.payments.sortedWith(compareByDescending<Payment>{it.date}.thenByDescending{it.time}).forEach{pay->
                    HorizontalDivider()
                    Text(display(pay.amount)+" · "+(ac.find{it.id==pay.account}?.name?:"Compte"))
                    Text(pay.date+" à "+pay.time.ifBlank{"heure non renseignée"})
                    Row{TextButton(onClick={paymentFor=d;paymentOld=pay}){Text("Modifier")};TextButton(onClick={paymentDeletion=d to pay}){Text("Supprimer")}}
                }
            }
        }
    }
    if(editing&&form!=null)DebtForm(form!!,ac,scope,{editing=false;form=null}){d->if(submit(d)){editing=false;form=null}}
    if(paymentFor!=null)PaymentForm(paymentFor!!,paymentOld,ac,{paymentFor=null;paymentOld=null}){pay->
        val d=paymentFor!!;val next=d.copy(payments=d.payments.filterNot{it.id==pay.id}+pay)
        if(submit(next)){paymentFor=null;paymentOld=null}
    }
    if(pending!=null)AlertDialog(onDismissRequest={pending=null},title={Text("Solde insuffisant")},text={Text("Confirmer malgré le solde négatif ?")},confirmButton={TextButton(onClick={val d=pending;pending=null;if(d!=null&&onSave(d)){editing=false;form=null;paymentFor=null;paymentOld=null;paymentDeletion=null}}){Text("Confirmer")}},dismissButton={TextButton(onClick={pending=null}){Text("Annuler")}})
    if(warning.isNotBlank())AlertDialog(onDismissRequest={warning=""},title={Text("Opération refusée")},text={Text(warning)},confirmButton={TextButton(onClick={warning=""}){Text("OK")}})
    if(deletion!=null)AlertDialog(onDismissRequest={deletion=null},title={Text("Supprimer cette fiche ?")},text={Text("Tous ses remboursements seront également supprimés et les soldes recalculés.")},confirmButton={TextButton(onClick={deletion?.let(onDelete);deletion=null}){Text("Supprimer")}},dismissButton={TextButton(onClick={deletion=null}){Text("Annuler")}})
    if(paymentDeletion!=null)AlertDialog(onDismissRequest={paymentDeletion=null},title={Text("Supprimer le remboursement ?")},text={Text("Le solde et le restant dû seront recalculés.")},confirmButton={TextButton(onClick={val pair=paymentDeletion;paymentDeletion=null;if(pair!=null)submit(pair.first.copy(payments=pair.first.payments.filterNot{it.id==pair.second.id}))}){Text("Supprimer")}},dismissButton={TextButton(onClick={paymentDeletion=null}){Text("Annuler")}})
}
@Composable private fun DebtForm(d:Obligation,ac:List<Account>,scope:String,close:()->Unit,save:(Obligation)->Unit){
    var person by remember{mutableStateOf(d.person)};var kind by remember{mutableStateOf(d.kind)};var amount by remember{mutableStateOf(if(d.amount==0L)"" else d.amount.toString())}
    var date by remember{mutableStateOf(d.date)};var time by remember{mutableStateOf(d.time)};var due by remember{mutableStateOf(d.due)}
    var space by remember{mutableStateOf(d.space)};var origin by remember{mutableStateOf(d.originAccount.orEmpty())};var note by remember{mutableStateOf(d.note)}
    val context=LocalContext.current
    val eligible=ac.filter{it.space==space}
    val n=amount.toLongOrNull()?:0
    AlertDialog(onDismissRequest=close,title={Text(if(d.amount==0L)"Nouvelle fiche" else "Modifier la fiche")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){
            if(d.payments.isEmpty())Choice("Type",kind,listOf("Dette","Créance")){kind=it}
            OutlinedTextField(person,{person=it},label={Text("Nom du débiteur ou créancier")},singleLine=true)
            MoneyField(amount,{amount=it},"Montant initial FCFA")
            if(d.payments.isEmpty()&&scope=="Tous")Choice("Espace",space,listOf("Personnel","Professionnel")){space=it;origin=""}
            Text("Date et heure de création");TimeFields(date,{date=it},time,{time=it})
            OutlinedButton(onClick={val day=runCatching{LocalDate.parse(due)}.getOrDefault(LocalDate.now());DatePickerDialog(context,{_,y,m,v->due=LocalDate.of(y,m+1,v).toString()},day.year,day.monthValue-1,day.dayOfMonth).show()},modifier=Modifier.fillMaxWidth()){Text("Échéance : "+due.ifBlank{"Facultative"})}
            if(due.isNotBlank())TextButton(onClick={due=""}){Text("Retirer l’échéance")}
            Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){Checkbox(checked=origin.isNotBlank(),onCheckedChange={origin=if(it)eligible.firstOrNull()?.id.orEmpty() else ""},enabled=eligible.isNotEmpty());Text(if(kind=="Dette")"Argent reçu sur un compte" else "Argent prêté depuis un compte")}
            if(origin.isNotBlank())AccountChoice("Compte initial",origin,eligible){origin=it}
            else Text("Sans mouvement initial : dette / créance déjà existante.")
            OutlinedTextField(note,{note=it},label={Text("Notes")})
        }
    },confirmButton={TextButton(enabled=person.isNotBlank()&&n>0&&n>=d.paid,onClick={save(d.copy(kind=kind,person=person.trim(),amount=n,date=date,time=time,due=due,space=space,originAccount=origin.ifBlank{null},note=note))}){Text("Enregistrer")}},dismissButton={TextButton(onClick=close){Text("Annuler")}})
}
@Composable private fun PaymentForm(d:Obligation,old:Payment?,ac:List<Account>,close:()->Unit,save:(Payment)->Unit){
    val eligible=ac.filter{it.space==d.space}
    var account by remember{mutableStateOf(old?.account?:eligible.firstOrNull()?.id.orEmpty())}
    var amount by remember{mutableStateOf(old?.amount?.toString()?:"")}
    var date by remember{mutableStateOf(old?.date?:LocalDate.now().toString())}
    var time by remember{mutableStateOf(old?.time?:nowTime())}
    val max=d.remaining+(old?.amount?:0)
    val n=amount.toLongOrNull()?:0
    AlertDialog(onDismissRequest=close,title={Text(if(old==null)"Remboursement" else "Modifier le remboursement")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(d.person+" · "+d.kind);MoneyField(amount,{amount=it},"Montant FCFA");TextButton(onClick={amount=max.toString()}){Text("Tout rembourser")};AccountChoice("Compte",account,eligible){account=it};TimeFields(date,{date=it},time,{time=it});if(eligible.isEmpty())Text("Crée d’abord un compte dans cet espace.")}},confirmButton={TextButton(enabled=n in 1..max&&account.isNotBlank(),onClick={save(Payment(old?.id?:uid(),account,n,date,time))}){Text("Enregistrer")}},dismissButton={TextButton(onClick=close){Text("Annuler")}})
}
