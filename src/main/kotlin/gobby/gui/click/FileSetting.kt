package gobby.gui.click

import gobby.Gobbyclient.Companion.mc
import gobby.utils.ConfigUtils
import java.io.File
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class FileSetting(
    name: String,
    fileName: String,
    folder: String,
    private val defaults: List<String> = emptyList(),
    desc: String = "",
    hidden: Boolean = false
) : Setting<Unit>(name, desc, Unit, hidden), ReadOnlyProperty<Any?, Set<String>> {

    private val file: File = File(ConfigUtils.directory(folder), fileName)

    private var loadedAt = -1L
    private var cached: Set<String> = emptySet()

    val entries: Set<String>
        get() {
            reloadIfChanged()
            return cached
        }

    fun open() {
        createIfMissing()
        mc.gui.setScreen(FileEditorScreen(this, mc.gui.screen()))
    }

    fun readText(): String {
        createIfMissing()
        return runCatching { file.readText() }.getOrDefault("")
    }

    fun writeText(text: String): Boolean {
        val written = runCatching { file.writeText(text) }.isSuccess
        reloadIfChanged()
        return written
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> = entries

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): FileSetting {
        thisRef.settings.add(this)
        return this
    }

    private fun reloadIfChanged() {
        createIfMissing()
        val stamp = file.lastModified()
        if (stamp == loadedAt) return
        loadedAt = stamp
        cached = runCatching { parse(file.readLines()) }.getOrDefault(cached)
    }

    private fun createIfMissing() {
        if (file.exists()) return
        val separator = System.lineSeparator()
        runCatching { file.writeText(defaults.joinToString(separator, postfix = separator)) }
    }

    private fun parse(lines: List<String>): Set<String> = lines
        .map { it.substringBefore("#").trim().uppercase() }
        .filter { it.isNotEmpty() }
        .toSet()
}
