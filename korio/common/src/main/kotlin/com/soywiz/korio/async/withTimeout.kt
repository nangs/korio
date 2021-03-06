package com.soywiz.korio.async

import com.soywiz.korio.CancellationException
import com.soywiz.korio.coroutine.Continuation
import com.soywiz.korio.coroutine.CoroutineContext
import com.soywiz.korio.coroutine.korioStartCoroutine

suspend fun <R> withTimeout(ms: Int, name: String = "timeout", callback: suspend () -> R): R = suspendCancellableCoroutine<R> { c ->
	var cancelled = false
	val timer = c.eventLoop.setTimeout(ms) {
		c.cancel(com.soywiz.korio.CancellationException("For $name"))
	}
	c.onCancel {
		cancelled = true
		timer.close()
		c.cancel()
	}
	callback.korioStartCoroutine(object : Continuation<R> {
		override val context: CoroutineContext = c.context

		override fun resume(value: R) {
			if (cancelled) return
			timer.close()
			c.resume(value)
		}

		override fun resumeWithException(exception: Throwable) {
			if (cancelled) return
			timer.close()
			c.resumeWithException(exception)
		}
	})
}

suspend fun <T> withOptTimeout(ms: Int?, name: String = "timeout", callback: suspend () -> T): T = suspendCancellableCoroutine<T> { c ->
	var cancelled = false
	val timer = when {
		ms == null -> null
		ms >= 0 -> c.eventLoop.setTimeout(ms) { c.cancel(CancellationException("For $name")) }
		else -> null
	}
	c.onCancel {
		cancelled = true
		timer?.close()
		c.cancel()
	}
	callback.korioStartCoroutine(object : Continuation<T> {
		override val context = c.context

		override fun resume(value: T) {
			if (cancelled) return
			timer?.close()
			c.resume(value)
		}

		override fun resumeWithException(exception: Throwable) {
			if (cancelled) return
			timer?.close()
			c.resumeWithException(exception)
		}
	})
}
