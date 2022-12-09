// !LANGUAGE: +ContextReceivers
// TARGET_BACKEND: JVM_IR

interface Id<A>{
    context(Unit)
    fun A.identity(): A
}

object StringId: Id<String>{
    context(Unit)
    override fun String.identity(): String = this
}

fun box(): String {
    with(Unit){
        with(StringId as Id<String>){
            return "OK".identity()
        }
    }
}