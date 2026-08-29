package com.fenyx.jtv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconsTest {

    @Test
    fun `maps common Jio categories`() {
        assertEquals("📰", CategoryIcons.emojiFor("News"))
        assertEquals("🏏", CategoryIcons.emojiFor("Sports"))
        assertEquals("🎬", CategoryIcons.emojiFor("Movies"))
        assertEquals("🧸", CategoryIcons.emojiFor("Kids"))
        assertEquals("🎵", CategoryIcons.emojiFor("Music"))
    }

    @Test
    fun `is case and whitespace insensitive`() {
        assertEquals(CategoryIcons.emojiFor("news"), CategoryIcons.emojiFor("  NEWS "))
    }

    @Test
    fun `unknown or empty categories get no icon`() {
        assertEquals("", CategoryIcons.emojiFor("Mystery Category"))
        assertEquals("", CategoryIcons.emojiFor(""))
    }

    @Test
    fun `decorate prefixes only when an icon exists`() {
        assertEquals("🏏 Sports", CategoryIcons.decorate("Sports"))
        assertEquals("Mystery", CategoryIcons.decorate("Mystery"))
    }

    @Test
    fun `keyword containment catches variants`() {
        assertTrue(CategoryIcons.emojiFor("Infotainment").isNotEmpty())
        assertTrue(CategoryIcons.emojiFor("Devotional").isNotEmpty())
        assertTrue(CategoryIcons.emojiFor("Food & Travel").isNotEmpty())
    }
}
