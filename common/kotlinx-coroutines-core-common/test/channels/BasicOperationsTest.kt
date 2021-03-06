/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.experimental.channels

import kotlinx.coroutines.experimental.*
import kotlin.coroutines.experimental.*
import kotlin.test.*

class BasicOperationsTest : TestBase() {

    @Test
    fun testSimpleSendReceive() = runTest {
        // Parametrized common test :(
        TestChannelKind.values().forEach { kind -> testSendReceive(kind, 100) }
    }

    @Test
    fun testOfferAfterClose() = runTest {
        TestChannelKind.values().forEach { kind -> testOffer(kind) }
    }

    @Test
    fun testReceiveOrNullAfterClose() = runTest {
        TestChannelKind.values().forEach { kind -> testReceiveOrNull(kind) }
    }

    @Test
    fun testReceiveOrNullAfterCloseWithException() = runTest {
        TestChannelKind.values().forEach { kind -> testReceiveOrNullException(kind) }
    }

    @Test
    fun testInvokeOnClose() = TestChannelKind.values().forEach { kind ->
        reset()
        val channel = kind.create()
        channel.invokeOnClose {
            if (it is AssertionError) {
                expect(3)
            }
        }
        expect(1)
        channel.offer(42)
        expect(2)
        channel.close(AssertionError())
        finish(4)
    }

    @Test
    fun testInvokeOnClosed() = TestChannelKind.values().forEach { kind ->
        reset()
        expect(1)
        val channel = kind.create()
        channel.close()
        channel.invokeOnClose { expect(2) }
        assertFailsWith<IllegalStateException> { channel.invokeOnClose { expect(3) } }
        finish(3)
    }

    @Test
    fun testMultipleInvokeOnClose() = TestChannelKind.values().forEach { kind ->
        reset()
        val channel = kind.create()
        channel.invokeOnClose { expect(3) }
        expect(1)
        assertFailsWith<IllegalStateException> { channel.invokeOnClose { expect(4) } }
        expect(2)
        channel.close()
        finish(4)
    }

    private suspend fun testReceiveOrNull(kind: TestChannelKind) {
        val channel = kind.create()
        val d = async(coroutineContext) {
            channel.receive()
        }

        yield()
        channel.close()
        assertTrue(channel.isClosedForReceive)

        assertNull(channel.receiveOrNull())
        assertNull(channel.poll())

        d.join()
        assertTrue(d.getCancellationException().cause is ClosedReceiveChannelException)
    }

    private suspend fun testReceiveOrNullException(kind: TestChannelKind) {
        val channel = kind.create()
        val d = async(coroutineContext) {
            channel.receive()
        }

        yield()
        channel.close(IndexOutOfBoundsException())
        assertTrue(channel.isClosedForReceive)

        assertFailsWith<IndexOutOfBoundsException> { channel.poll() }
        try {
            channel.receiveOrNull()
            fail()
        } catch (e: IndexOutOfBoundsException) {
            // Expected
        }

        d.join()
        assertTrue(d.getCancellationException().cause is IndexOutOfBoundsException)
    }


    private suspend fun testOffer(kind: TestChannelKind) {
        val channel = kind.create()
        val d = async(coroutineContext) { channel.send(42) }
        yield()
        channel.close()

        assertTrue(channel.isClosedForSend)
        try {
            channel.offer(2)
            fail()
        } catch (e: ClosedSendChannelException) {
            if (!kind.isConflated) {
                assertEquals(42, channel.receive())
            }
        }

        d.await()
    }

    private suspend fun testSendReceive(kind: TestChannelKind, iterations: Int) {
        val channel = kind.create()

        launch(coroutineContext) {
            repeat(iterations) { channel.send(it) }
            channel.close()
        }
        var expected = 0
        for (x in channel) {
            if (!kind.isConflated) {
                assertEquals(expected++, x)
            } else {
                assertTrue(x >= expected)
                expected = x + 1
            }
        }
        if (!kind.isConflated) {
            assertEquals(iterations, expected)
        }
    }
}
