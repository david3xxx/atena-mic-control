import pe.atena.mic.VoiceMilestones

fun run(label:String, block:()->Unit) { block(); println("APROBADO | $label") }
fun main() {
 run("1: ChatGPT aparece - UNO") { val x=VoiceMilestones(); check(x.confirm(1)) }
 run("2: SD abierto - DOS") { val x=VoiceMilestones(); check(x.confirm(1)); check(x.confirm(2)) }
 run("3: voz confirmada - TRES") { val x=VoiceMilestones(); check(x.confirm(1)); check(x.confirm(2)); check(x.confirm(3)); check(x.completed()) }
 run("4: nunca anunciar TRES antes de DOS") { val x=VoiceMilestones(); check(x.confirm(1)); check(!x.confirm(3)); check(x.confirm(2)) }
 run("5: eventos duplicados no repiten números") { val x=VoiceMilestones(); for(i in 1..3){check(x.confirm(i));repeat(5){check(!x.confirm(i))}} }
 run("6: segundo WASH vuelve a decir UNO DOS TRES") { val x=VoiceMilestones(); repeat(2){ if(it>0)x.reset(); for(i in 1..3)check(x.confirm(i)) } }
 run("7: décimo WASH también funciona") { val x=VoiceMilestones(); repeat(10){x.reset();for(i in 1..3)check(x.confirm(i))} }
 run("8: rechaza estados inesperados") { val x=VoiceMilestones(); check(!x.confirm(0));check(!x.confirm(4));check(!x.hasConfirmed(2)) }
}
