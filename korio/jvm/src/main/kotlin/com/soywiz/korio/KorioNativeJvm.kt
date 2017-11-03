package com.soywiz.korio

import com.soywiz.korio.async.*
import com.soywiz.korio.net.AsyncSocketFactory
import com.soywiz.korio.net.JvmAsyncSocketFactory
import com.soywiz.korio.net.http.HttpClient
import com.soywiz.korio.net.http.HttpClientJvm
import com.soywiz.korio.net.http.HttpFactory
import com.soywiz.korio.net.http.HttpServer
import com.soywiz.korio.net.ws.WebSocketClientFactory
import com.soywiz.korio.vfs.LocalVfsJvm
import com.soywiz.korio.vfs.MemoryVfs
import com.soywiz.korio.vfs.ResourcesVfsProviderJvm
import com.soywiz.korio.vfs.VfsFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


actual typealias Synchronized = kotlin.jvm.Synchronized
actual typealias JvmField = kotlin.jvm.JvmField
actual typealias JvmStatic = kotlin.jvm.JvmStatic
actual typealias JvmOverloads = kotlin.jvm.JvmOverloads
actual typealias Transient = kotlin.jvm.Transient

actual typealias Language = org.intellij.lang.annotations.Language

actual typealias IOException = java.io.IOException
actual typealias EOFException = java.io.EOFException
actual typealias FileNotFoundException = java.io.FileNotFoundException

actual typealias RuntimeException = java.lang.RuntimeException
actual typealias IllegalStateException = java.lang.IllegalStateException
actual typealias CancellationException = java.util.concurrent.CancellationException

actual class Semaphore actual constructor(initial: Int) {
	val jsema = java.util.concurrent.Semaphore(initial)
	//var initial: Int
	actual fun acquire() = jsema.acquire()

	actual fun release() = jsema.release()
}

actual object KorioNative {
	actual val currentThreadId: Long get() = Thread.currentThread().id

	actual abstract class NativeThreadLocal<T> {
		actual abstract fun initialValue(): T

		val jthreadLocal = object : ThreadLocal<T>() {
			override fun initialValue(): T = this@NativeThreadLocal.initialValue()
		}

		actual fun get(): T = jthreadLocal.get()
		actual fun set(value: T) = jthreadLocal.set(value)
	}

	actual suspend fun <T> executeInWorker(callback: suspend () -> T): T = executeInWorkerSafer(callback)

	actual val platformName: String = "jvm"
	actual val rawOsName: String by lazy { System.getProperty("os.name") }

	private val secureRandom: SecureRandom by lazy { SecureRandom.getInstanceStrong() }

	actual fun getRandomValues(data: ByteArray): Unit {
		secureRandom.nextBytes(data)
	}

	actual val httpFactory: HttpFactory by lazy {
		object : HttpFactory {
			init {
				System.setProperty("http.keepAlive", "false")
			}

			override fun createClient(): HttpClient = HttpClientJvm()
			override fun createServer(): HttpServer = KorioNativeDefaults.createServer()
		}
	}

	actual class NativeCRC32 {
		val crc32 = java.util.zip.CRC32()

		actual fun update(data: ByteArray, offset: Int, size: Int) {
			crc32.update(data, offset, size)
		}

		actual fun digest(): Int {
			return crc32.value.toInt()
		}
	}

	actual class SimplerMessageDigest actual constructor(name: String) {
		val md = MessageDigest.getInstance(name)

		actual suspend fun update(data: ByteArray, offset: Int, size: Int) = executeInWorkerSafer { md.update(data, offset, size) }
		actual suspend fun digest(): ByteArray = executeInWorkerSafer { md.digest() }
	}

	actual class SimplerMac actual constructor(name: String, key: ByteArray) {
		val mac = Mac.getInstance(name).apply { init(SecretKeySpec(key, name)) }
		actual suspend fun update(data: ByteArray, offset: Int, size: Int) = executeInWorkerSafer { mac.update(data, offset, size) }
		actual suspend fun finalize(): ByteArray = executeInWorkerSafer { mac.doFinal() }
	}

	actual object SyncCompression {
		actual fun inflate(data: ByteArray): ByteArray {
			val out = ByteArrayOutputStream()
			val s = InflaterInputStream(ByteArrayInputStream(data))
			val temp = ByteArray(0x1000)
			while (true) {
				val read = s.read(temp, 0, temp.size)
				if (read <= 0) break
				out.write(temp, 0, read)
			}
			return out.toByteArray()
		}

		actual fun inflateTo(data: ByteArray, out: ByteArray): ByteArray {
			val s = InflaterInputStream(ByteArrayInputStream(data))
			var pos = 0
			var remaining = out.size
			while (true) {
				val read = s.read(out, pos, remaining)
				if (read <= 0) break
				pos += read
				remaining -= read
			}
			return out
		}

		actual fun deflate(data: ByteArray, level: Int): ByteArray {
			return DeflaterInputStream(ByteArrayInputStream(data), Deflater(level)).readBytes()
		}
	}

	actual class Inflater actual constructor(val nowrap: Boolean) {
		val ji = java.util.zip.Inflater(nowrap)

		actual fun needsInput(): Boolean = ji.needsInput()
		actual fun setInput(buffer: ByteArray) = ji.setInput(buffer)
		actual fun inflate(buffer: ByteArray, offset: Int, len: Int): Int = ji.inflate(buffer, offset, len)
		actual fun end() = ji.end()
	}

	actual fun Thread_sleep(time: Long) = Thread.sleep(time)

	actual val eventLoopFactoryDefaultImpl: EventLoopFactory = EventLoopFactoryJvmAndCSharp()

	actual val asyncSocketFactory: AsyncSocketFactory by lazy { JvmAsyncSocketFactory() }
	actual val websockets: WebSocketClientFactory get() = TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
	actual val File_separatorChar: Char by lazy { File.separatorChar }

	actual fun rootLocalVfs(): VfsFile = localVfs(".")
	actual fun applicationVfs(): VfsFile = localVfs(File(".").absolutePath)
	actual fun cacheVfs(): VfsFile = MemoryVfs()
	actual fun externalStorageVfs(): VfsFile = localVfs(".")
	actual fun userHomeVfs(): VfsFile = localVfs(".")
	actual fun tempVfs(): VfsFile = localVfs(tmpdir)
	actual fun localVfs(path: String): VfsFile = LocalVfsJvm()[path]

	actual val ResourcesVfs: VfsFile by lazy { ResourcesVfsProviderJvm()().root }

	val tmpdir: String get() = System.getProperty("java.io.tmpdir")

	actual fun <T> copyRangeTo(src: Array<T>, srcPos: Int, dst: Array<T>, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: BooleanArray, srcPos: Int, dst: BooleanArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: ByteArray, srcPos: Int, dst: ByteArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: ShortArray, srcPos: Int, dst: ShortArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: IntArray, srcPos: Int, dst: IntArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: LongArray, srcPos: Int, dst: LongArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: FloatArray, srcPos: Int, dst: FloatArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun copyRangeTo(src: DoubleArray, srcPos: Int, dst: DoubleArray, dstPos: Int, count: Int) = System.arraycopy(src, srcPos, dst, dstPos, count)
	actual fun <T> fill(src: Array<T>, value: T, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)
	actual fun fill(src: BooleanArray, value: Boolean, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)
	actual fun fill(src: ByteArray, value: Byte, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)
	actual fun fill(src: ShortArray, value: Short, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)
	actual fun fill(src: IntArray, value: Int, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)
	actual fun fill(src: FloatArray, value: Float, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)
	actual fun fill(src: DoubleArray, value: Double, from: Int, to: Int) = java.util.Arrays.fill(src, from, to, value)

	actual fun enterDebugger() = Unit
	actual fun printStackTrace(e: Throwable) = e.printStackTrace()
	actual fun log(msg: Any?): Unit = java.lang.System.out.println(msg)
	actual fun error(msg: Any?): Unit = java.lang.System.err.println(msg)

	actual suspend fun uncompressGzip(data: ByteArray): ByteArray = executeInWorker {
		val out = ByteArrayOutputStream()
		GZIPInputStream(ByteArrayInputStream(data)).copyTo(out)
		return@executeInWorker out.toByteArray()
	}

	actual suspend fun uncompressZlib(data: ByteArray): ByteArray = executeInWorker {
		val out = ByteArrayOutputStream()
		InflaterInputStream(ByteArrayInputStream(data)).copyTo(out)
		return@executeInWorker out.toByteArray()
	}

	actual suspend fun compressGzip(data: ByteArray, level: Int): ByteArray = executeInWorker {
		val out = ByteArrayOutputStream()
		val out2 = GZIPOutputStream(out)
		ByteArrayInputStream(data).copyTo(out2)
		out2.flush()
		return@executeInWorker out.toByteArray()
	}

	actual suspend fun compressZlib(data: ByteArray, level: Int): ByteArray = executeInWorker {
		val out = ByteArrayOutputStream()
		val deflater = Deflater(level)
		val out2 = DeflaterOutputStream(out, deflater)
		ByteArrayInputStream(data).copyTo(out2)
		out2.flush()
		return@executeInWorker out.toByteArray()
	}

	@Suppress("unused")
	actual class FastMemory(buffer: ByteBuffer, actual val size: Int) {
		private val _buffer: ByteBuffer = buffer
		private val _i16 = buffer.asShortBuffer()
		private val _i32 = buffer.asIntBuffer()
		private val _f32 = buffer.asFloatBuffer()

		// Exposed to libraries
		val buffer get() = _buffer
		val i16 get() = _i16
		val i32 get() = _i32
		val f32 get() = _f32

		companion actual object {
			actual fun alloc(size: Int): FastMemory = FastMemory(ByteBuffer.allocateDirect((size + 0xF) and 0xF.inv()).order(ByteOrder.nativeOrder()), size)

			actual fun copy(src: FastMemory, srcPos: Int, dst: FastMemory, dstPos: Int, length: Int): Unit {
				val srcBuffer = src.buffer.duplicate()
				srcBuffer.position(srcPos)
				srcBuffer.limit(srcPos + length)
				dst.buffer.position(dstPos)
				dst.buffer.put(srcBuffer)
			}

			actual fun copy(src: FastMemory, srcPos: Int, dst: ByteArray, dstPos: Int, length: Int): Unit {
				src.buffer.position(srcPos)
				src.buffer.get(dst, dstPos, length)
			}

			actual fun copy(src: ByteArray, srcPos: Int, dst: FastMemory, dstPos: Int, length: Int): Unit {
				dst.buffer.position(dstPos)
				dst.buffer.put(src, srcPos, length)
			}

			actual fun copyAligned(src: FastMemory, srcPosAligned: Int, dst: ShortArray, dstPosAligned: Int, length: Int): Unit {
				src._i16.position(srcPosAligned)
				src._i16.get(dst, dstPosAligned, length)
			}

			actual fun copyAligned(src: ShortArray, srcPosAligned: Int, dst: FastMemory, dstPosAligned: Int, length: Int): Unit {
				dst._i16.position(dstPosAligned)
				dst._i16.put(src, srcPosAligned, length)
			}

			actual fun copyAligned(src: FastMemory, srcPosAligned: Int, dst: IntArray, dstPosAligned: Int, length: Int): Unit {
				src._i32.position(srcPosAligned)
				src._i32.get(dst, dstPosAligned, length)
			}

			actual fun copyAligned(src: IntArray, srcPosAligned: Int, dst: FastMemory, dstPosAligned: Int, length: Int): Unit {
				dst._i32.position(dstPosAligned)
				dst._i32.put(src, srcPosAligned, length)
			}

			actual fun copyAligned(src: FastMemory, srcPosAligned: Int, dst: FloatArray, dstPosAligned: Int, length: Int): Unit {
				src._f32.position(srcPosAligned)
				src._f32.get(dst, dstPosAligned, length)
			}

			actual fun copyAligned(src: FloatArray, srcPosAligned: Int, dst: FastMemory, dstPosAligned: Int, length: Int): Unit {
				dst._f32.position(dstPosAligned)
				dst._f32.put(src, srcPosAligned, length)
			}
		}

		actual operator fun get(index: Int): Int = _buffer.get(index).toInt() and 0xFF
		actual operator fun set(index: Int, value: Int): Unit = run { _buffer.put(index, value.toByte()) }

		actual fun setAlignedInt16(index: Int, value: Short): Unit = run { _i16.put(index, value) }
		actual fun getAlignedInt16(index: Int): Short = _i16.get(index)
		actual fun setAlignedInt32(index: Int, value: Int): Unit = run { _i32.put(index, value) }
		actual fun getAlignedInt32(index: Int): Int = _i32.get(index)
		actual fun setAlignedFloat32(index: Int, value: Float): Unit = run { _f32.put(index, value) }
		actual fun getAlignedFloat32(index: Int): Float = _f32.get(index)

		actual fun getInt16(index: Int): Short = _buffer.getShort(index)
		actual fun setInt16(index: Int, value: Short): Unit = run { _buffer.putShort(index, value) }
		actual fun setInt32(index: Int, value: Int): Unit = run { _buffer.putInt(index, value) }
		actual fun getInt32(index: Int): Int = _buffer.getInt(index)
		actual fun setFloat32(index: Int, value: Float): Unit = run { _buffer.putFloat(index, value) }
		actual fun getFloat32(index: Int): Float = _buffer.getFloat(index)


		actual fun setArrayInt8(dstPos: Int, src: ByteArray, srcPos: Int, len: Int) = copy(src, srcPos, this, dstPos, len)
		actual fun setAlignedArrayInt8(dstPos: Int, src: ByteArray, srcPos: Int, len: Int) = copy(src, srcPos, this, dstPos, len)
		actual fun setAlignedArrayInt16(dstPos: Int, src: ShortArray, srcPos: Int, len: Int) = copyAligned(src, srcPos, this, dstPos, len)
		actual fun setAlignedArrayInt32(dstPos: Int, src: IntArray, srcPos: Int, len: Int) = copyAligned(src, srcPos, this, dstPos, len)
		actual fun setAlignedArrayFloat32(dstPos: Int, src: FloatArray, srcPos: Int, len: Int) = copyAligned(src, srcPos, this, dstPos, len)

		actual fun getArrayInt8(srcPos: Int, dst: ByteArray, dstPos: Int, len: Int) = copy(this, srcPos, dst, dstPos, len)
		actual fun getAlignedArrayInt8(srcPos: Int, dst: ByteArray, dstPos: Int, len: Int) = copy(this, srcPos, dst, dstPos, len)
		actual fun getAlignedArrayInt16(srcPos: Int, dst: ShortArray, dstPos: Int, len: Int) = copyAligned(this, srcPos, dst, dstPos, len)
		actual fun getAlignedArrayInt32(srcPos: Int, dst: IntArray, dstPos: Int, len: Int) = copyAligned(this, srcPos, dst, dstPos, len)
		actual fun getAlignedArrayFloat32(srcPos: Int, dst: FloatArray, dstPos: Int, len: Int) = copyAligned(this, srcPos, dst, dstPos, len)
	}

	actual fun syncTest(block: suspend EventLoopTest.() -> Unit): Unit {
		sync(el = EventLoopTest(), step = 10, block = block)
	}
}
