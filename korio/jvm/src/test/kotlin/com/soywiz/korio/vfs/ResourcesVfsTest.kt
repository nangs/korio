package com.soywiz.korio.vfs

import com.soywiz.korio.async.*
import org.junit.Assert
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class ResourcesVfsTest {
	@Test
	fun name() = syncTest {
		println("[A]")
		val listing = ResourcesVfs["tresfolder"].list()
		println("[B]")

		for (v in ResourcesVfs["tresfolder"].list().filter { it.extensionLC == "txt" }.toList()) {
			println(v)
		}

		assertEquals(
			"[a.txt, b.txt]",
			ResourcesVfs["tresfolder"].list().filter { it.extensionLC == "txt" }.toList().map { it.basename }.sorted().toString()
		)
	}
}
