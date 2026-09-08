package gobby.gui.click

import java.awt.Color
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class StringSetting(
    name: String,
    default: String = "",
    desc: String = "",
    hidden: Boolean = false,
    val length: Int = 50,
    val onCommit: (String) -> Unit = {}
) : Setting<String>(name, desc, default, hidden), ReadOnlyProperty<Any?, String> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): StringSetting {
        thisRef.settings.add(this)
        return this
    }
}

interface ChoiceOptions {
    val options: List<String>
    val summary: String
    val closesOnPick: Boolean
    fun isChosen(index: Int): Boolean
    fun pick(index: Int)
}

class MultipleChoiceSetting(
    name: String,
    override val options: List<String>,
    default: Set<String> = emptySet(),
    desc: String = "",
    hidden: Boolean = false
) : Setting<MutableSet<String>>(name, desc, default.toMutableSet(), hidden),
    ReadOnlyProperty<Any?, Set<String>>, ChoiceOptions {

    override fun getValue(thisRef: Any?, property: KProperty<*>): Set<String> = value

    override val summary: String
        get() = when {
            value.isEmpty() -> "None"
            value.size == options.size -> "All"
            else -> options.filter { it in value }.joinToString(", ")
        }

    override val closesOnPick = false

    override fun isChosen(index: Int): Boolean = options.getOrNull(index) in value

    override fun pick(index: Int) {
        val option = options.getOrNull(index) ?: return
        if (!value.remove(option)) value.add(option)
    }

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): MultipleChoiceSetting {
        thisRef.settings.add(this)
        return this
    }
}

class SelectorSetting(
    name: String,
    default: Int = 0,
    override val options: List<String>,
    desc: String = "",
    hidden: Boolean = false
) : Setting<Int>(name, desc, default, hidden), ReadWriteProperty<Any?, Int>, ChoiceOptions {

    override val summary: String get() = options.getOrElse(value) { "?" }

    override val closesOnPick = true

    override fun isChosen(index: Int): Boolean = index == value

    override fun pick(index: Int) { value = index }

    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Int) { value = v.coerceIn(0, options.lastIndex) }

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    fun childOf(dropdown: DropDownSetting) = apply { parentDropdown = dropdown; dropdown.children.add(this) }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): SelectorSetting {
        thisRef.settings.add(this)
        return this
    }
}

class ColorSetting(
    name: String,
    default: Color = Color.WHITE,
    desc: String = "",
    hidden: Boolean = false,
    var expanded: Boolean = false,
    var cachedHue: Float = -1f
) : Setting<Color>(name, desc, default, hidden), ReadWriteProperty<Any?, Color> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, v: Color) { value = v }

    fun withDependency(condition: () -> Boolean) = apply { dependency = condition }

    operator fun provideDelegate(thisRef: Module, property: KProperty<*>): ColorSetting {
        thisRef.settings.add(this)
        return this
    }
}
