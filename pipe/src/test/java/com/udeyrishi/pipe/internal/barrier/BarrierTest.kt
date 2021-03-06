/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.jodah.concurrentunit.Waiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

@RunWith(JUnit4::class)
class BarrierTest {
    @get:Rule
    val mockRule: MockitoRule = MockitoJUnit.rule()

    @Mock
    internal lateinit var mockController: BarrierController<String>

    @Test
    fun worksIfLiftedAfterStart() {
        val barrier = BarrierImpl(mockController)
        var result: String? = null
        val job = GlobalScope.launch {
            result = barrier.invoke("input")
        }

        assertTrue(job.isActive)
        assertNull(result)

        barrier.lift()
        runBlocking {
            job.join()
        }

        assertFalse(job.isActive)
        assertEquals("input", result)
    }

    @Test
    fun worksIfLiftedBeforeStart() {
        val barrier = BarrierImpl(mockController)
        barrier.lift()

        var result: String? = null
        val job = GlobalScope.launch {
            result = barrier.invoke("input")
        }

        runBlocking {
            job.join()
        }

        assertFalse(job.isActive)
        assertEquals("input", result)
    }

    @Test(expected = IllegalStateException::class)
    fun `fails if invoked multiple times`() {
        val barrier = BarrierImpl(mockController)

        val firstDeferredResult = GlobalScope.async {
            barrier.invoke("input1")
        }

        barrier.lift()

        runBlocking {
            firstDeferredResult.await()
        }

        val exception: Throwable? = runBlocking {
            try {
                barrier.invoke("input2")
                null
            } catch (e: Throwable) {
                e
            }
        }

        exception?.let {
            throw it
        }
    }

    @Test
    fun callsControllerUponCreation() {
        verify(mockController, never()).onBarrierCreated(any())
        val barrier = BarrierImpl(mockController)
        verify(mockController).onBarrierCreated(eq(barrier))
    }

    @Test
    fun callsControllerUponBlock() {
        val waiter = Waiter()
        val mockController = mock<BarrierController<String>>()

        whenever(mockController.onBarrierBlocked(any())).doAnswer {
            waiter.resume()
        }

        val barrier = BarrierImpl(mockController)
        verify(mockController, never()).onBarrierBlocked(any())
        val job = GlobalScope.launch {
            barrier.invoke("this")
        }

        waiter.await()

        barrier.lift()

        runBlocking {
            job.join()
        }

        verify(mockController).onBarrierBlocked(barrier)
    }

    @Test
    fun doesNotCallControllerUponBlockIfLiftedBeforeStart() {
        val barrier = BarrierImpl(mockController)
        verify(mockController, never()).onBarrierBlocked(any())
        barrier.lift()

        runBlocking {
            barrier.invoke("this")
        }

        verify(mockController, never()).onBarrierBlocked(any())
    }

    @Test
    fun canSetCustomResultIfLiftedBeforeStart() {
        val barrier = BarrierImpl(mockController)
        barrier.lift("that")

        val result = runBlocking {
            barrier.invoke("this")
        }

        assertEquals("that", result)
    }

    @Test
    fun canSetCustomResultIfLiftedAfterStart() {
        val barrier = BarrierImpl(mockController)

        val asyncResult = GlobalScope.async {
            barrier.invoke("this")
        }

        barrier.lift("that")

        val result = runBlocking {
            asyncResult.await()
        }

        assertEquals("that", result)
    }

    @Test
    fun `interruption lifts the barrier with a null result`() {
        val barrier = BarrierImpl(mockController)
        val asyncResult = GlobalScope.async {
            barrier.invoke("this")
        }

        barrier.interrupt()
        val result = runBlocking {
            asyncResult.await()
        }

        assertNull(result)
    }

    @Test
    fun `interrupting a lifted barrier is a no-op`() {
        val barrier = BarrierImpl(mockController)
        val asyncResult = GlobalScope.async {
            barrier.invoke("this")
        }

        barrier.lift("that")
        barrier.interrupt()
        val result = runBlocking {
            asyncResult.await()
        }

        assertEquals("that", result)
    }

    @Test
    fun `lifting an interrupted barrier still gives a null result`() {
        val barrier = BarrierImpl(mockController)
        val asyncResult = GlobalScope.async {
            barrier.invoke("this")
        }

        barrier.interrupt()
        barrier.lift("that")
        val result = runBlocking {
            asyncResult.await()
        }

        assertNull(result)
    }

    @Test
    fun `informs controller of interruption`() {
        val barrier = BarrierImpl(mockController)
        GlobalScope.launch {
            barrier.invoke("this")
        }

        verify(mockController, never()).onBarrierInterrupted(any())
        barrier.interrupt()
        verify(mockController).onBarrierInterrupted(eq(barrier))
    }
}
