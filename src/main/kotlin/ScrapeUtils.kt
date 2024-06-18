package cz.lastaapps

import it.skrape.selects.CssSelectable
import it.skrape.selects.DocElement
import it.skrape.selects.ElementNotFoundException

internal fun <T> CssSelectable.tryFindByIndex(
    index: Int,
    cssSelector: String = "",
    init: DocElement.() -> T,
): T? {
    try {
        // tests if the element exists
        findByIndex(index, cssSelector)
    } catch (e: ElementNotFoundException) {
        return null
    }
    // is placed outside of the try-catch, so Errors in the init block aren't caught
    return findByIndex(index, cssSelector, init)
}

internal fun <T> CssSelectable.tryFindFirst(
    cssSelector: String = "",
    init: DocElement.() -> T,
): T? {
    try {
        findFirst(cssSelector)
    } catch (e: ElementNotFoundException) {
        return null
    }
    return findFirst(cssSelector, init)
}

internal fun <T> CssSelectable.tryFindAll(
    cssSelector: String = "",
    init: List<DocElement>.() -> T,
): T? {
    try {
        findFirst(cssSelector)
    } catch (e: ElementNotFoundException) {
        return null
    }
    return findAll(cssSelector, init)
}

internal fun <T> CssSelectable.tryFindAllAndCycle(
    cssSelector: String = "",
    init: DocElement.() -> T,
) {
    try {
        findFirst(cssSelector)
    } catch (e: ElementNotFoundException) {
        return
    }
    return findAllAndCycle(cssSelector, init)
}

internal fun <T> CssSelectable.findAllAndCycle(
    cssSelector: String = "",
    init: DocElement.() -> T,
) {
    findAll(cssSelector) {
        forEach {
            with(it) {
                init()
            }
        }
    }
}

internal fun <E> Collection<E>.forEachApply(action: E.() -> Unit) {
    this.forEach {
        with(it) {
            action()
        }
    }
}

internal fun String.replaceSpaces(with: String = ""): String =
    replace("\\p{Zs}+".toRegex(), with)
        .replace("&nbsp; ", with).trim()
