package org.kotlindotnet.compiler

/**
 * Контекст состояния IL-эмиттера.
 *
 * Контракт «IL корректен» (A-03): эмиттер ведёт стек контекстов.
 * Каждый `beginX` пушит состояние, `endX` — попает с проверкой
 * парности. Опкоды/метки/локалки разрешены только в [Method].
 *
 * Использует sealed class (а не enum), т.к. состояния несут данные
 * (например, имя класса/метода) — удобно для диагностики.
 *
 * См. ADR 0005 — лямбды `IlEmitter.() -> Unit` НЕ используем,
 * только явные `begin/end` пары.
 */
sealed class IlContext {

    /** Верхний уровень: assembly/module объявлен, классы/методы ещё нет. */
    object TopLevel : IlContext() {
        override fun toString() = "TopLevel"
    }

    /** Внутри `.class`-контейнера (top-level функции пакуем в `<File>Kt`). */
    data class ContainerClass(
        val namespace: String,
        val className: String
    ) : IlContext()

    /** Внутри `.method` — единственное состояние, где разрешены опкоды. */
    data class Method(
        val name: String,
        val returnType: String,
        val isEntrypoint: Boolean
    ) : IlContext()
}
