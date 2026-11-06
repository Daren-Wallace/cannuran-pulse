package localtest

import java.io.File

fun main(args: Array<String>) {
    val root = File(args[0])
    var pass = 0; var failN = 0
    root.walkTopDown().filter { it.name.endsWith("Test.class") && !it.name.contains('$') }.sorted().forEach { f ->
        val name = f.relativeTo(root).path.removeSuffix(".class").replace(File.separatorChar, '.')
        val cls = Class.forName(name)
        for (m in cls.declaredMethods.filter { it.isAnnotationPresent(org.junit.Test::class.java) }.sortedBy { it.name }) {
            try { m.invoke(cls.getDeclaredConstructor().newInstance()); pass++; println("  PASS ${cls.simpleName}.${m.name}") }
            catch (e: java.lang.reflect.InvocationTargetException) {
                failN++; val c = e.targetException
                println("  FAIL ${cls.simpleName}.${m.name}: $c")
                c.stackTrace.firstOrNull { it.className.startsWith("capital") }?.let { println("       at $it") }
            }
        }
    }
    println("\n$pass passed, $failN failed")
    if (failN > 0) System.exit(1)
}
