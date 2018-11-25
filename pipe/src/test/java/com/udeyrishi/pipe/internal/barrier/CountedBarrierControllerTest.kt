/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.barrier

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.udeyrishi.pipe.internal.util.createEffectiveContext
import com.udeyrishi.pipe.testutil.DefaultTestDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class CountedBarrierControllerTest {
    @get:Rule
    val timeoutRule = Timeout(25, TimeUnit.SECONDS)

    private lateinit var mockBarrier1: Barrier<String>
    private lateinit var mockBarrier2: Barrier<String>
    private lateinit var mockBarrier3: Barrier<String>
    private var barrier1Lifted = false
    private var barrier2Lifted = false
    private var barrier3Lifted = false

    private lateinit var controller: CountedBarrierControllerImpl<String>

    @Before
    fun setup() {
        barrier1Lifted = false
        barrier2Lifted = false
        barrier3Lifted = false

        mockBarrier1 = mock {
            on { input } doReturn "mockInput1"
            on { lift(any()) } doAnswer {
                barrier1Lifted = true
                Unit
            }
        }

        mockBarrier2 = mock {
            on { input } doReturn "mockInput2"
            on { lift(any()) } doAnswer {
                barrier2Lifted = true
                Unit
            }
        }

        mockBarrier3 = mock {
            on { input } doReturn "mockInput3"
            on { lift(any()) } doAnswer {
                barrier3Lifted = true
                Unit
            }
        }

        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = DefaultTestDispatcher.createEffectiveContext())
    }

    @Test(expected = IllegalStateException::class)
    fun `onBarrierCreated checks for capacity overflows`() {
        controller.setCapacity(1)
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
    }

    @Test
    fun `lifts barrier when arrival count matches capacity`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        controller.onBarrierBlocked(mockBarrier1)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier2)

        while (!barrier1Lifted || !barrier2Lifted) { }

        verify(mockBarrier1, times(1)).lift(eq("mockInput1"))
        verify(mockBarrier2, times(1)).lift(eq("mockInput2"))
    }

    @Test
    fun `can update capacity to a bigger value when blocked`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier1)
        controller.setCapacity(3)
        controller.onBarrierCreated(mockBarrier3)
        controller.onBarrierBlocked(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())
        verify(mockBarrier3, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier3)

        while (!barrier1Lifted || !barrier2Lifted || !barrier3Lifted) { }

        verify(mockBarrier1, times(1)).lift(eq("mockInput1"))
        verify(mockBarrier2, times(1)).lift(eq("mockInput2"))
        verify(mockBarrier3, times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `can update capacity to a lower value when blocked`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = DefaultTestDispatcher.createEffectiveContext())

        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier2)
        controller.onBarrierCreated(mockBarrier3)

        controller.setCapacity(3)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())
        verify(mockBarrier3, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier3)

        while (!barrier1Lifted || !barrier2Lifted || !barrier3Lifted) { }

        verify(mockBarrier1, times(1)).lift(eq("mockInput1"))
        verify(mockBarrier2, times(1)).lift(eq("mockInput2"))
        verify(mockBarrier3, times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `can update capacity to a bigger value before start`() {
        controller.setCapacity(3)
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierCreated(mockBarrier3)
        controller.onBarrierBlocked(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())
        verify(mockBarrier3, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier3)

        while (!barrier1Lifted || !barrier2Lifted || !barrier3Lifted) { }

        verify(mockBarrier1, times(1)).lift(eq("mockInput1"))
        verify(mockBarrier2, times(1)).lift(eq("mockInput2"))
        verify(mockBarrier3, times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `can update capacity to a lower value before start`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = DefaultTestDispatcher.createEffectiveContext())
        controller.setCapacity(3)

        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier2)
        controller.onBarrierCreated(mockBarrier3)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())
        verify(mockBarrier3, never()).lift(any())

        controller.onBarrierBlocked(mockBarrier3)

        while (!barrier1Lifted || !barrier2Lifted || !barrier3Lifted) { }

        verify(mockBarrier1, times(1)).lift(eq("mockInput1"))
        verify(mockBarrier2, times(1)).lift(eq("mockInput2"))
        verify(mockBarrier3, times(1)).lift(eq("mockInput3"))
    }

    @Test
    fun `updating capacity to arrival count lifts the barriers`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = DefaultTestDispatcher.createEffectiveContext())

        var mockBarrier1Result: Any? = null
        mockBarrier1 = mock {
            on { input } doReturn "mockInput1"
            on { lift(any()) } doAnswer { invocation ->
                mockBarrier1Result = invocation.arguments[0]
                Unit
            }
        }

        var mockBarrier2Result: Any? = null
        mockBarrier2 = mock {
            on { input } doReturn "mockInput2"
            on { lift(any()) } doAnswer { invocation ->
                mockBarrier2Result = invocation.arguments[0]
                Unit
            }
        }

        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier1)

        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        controller.setCapacity(2)
        while (mockBarrier1Result == null || mockBarrier2Result == null) {
            Thread.sleep(100)
        }
        verify(mockBarrier1, times(1)).lift(eq("mockInput1"))
        verify(mockBarrier2, times(1)).lift(eq("mockInput2"))
    }

    @Test(expected = IllegalStateException::class)
    fun `cannot update capacity to a value less than the registration count`() {
        controller = CountedBarrierControllerImpl(capacity = 4, launchContext = DefaultTestDispatcher.createEffectiveContext())
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)

        controller.setCapacity(1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `onBarrierBlocked verifies that the barrier was registered`() {
        controller.onBarrierBlocked(mockBarrier1)
    }

    @Test
    fun `invokes onBarrierLiftedAction when supplied`() {
        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = DefaultTestDispatcher.createEffectiveContext()) {
            // the inputs come in sorted
            assertEquals(listOf("mockInput1", "mockInput2"), it)
            listOf("mockResult1", "mockResult2")
        }

        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierCreated(mockBarrier1)

        controller.onBarrierBlocked(mockBarrier2)

        verify(mockBarrier1, never()).lift(any())
        verify(mockBarrier2, never()).lift(any())

        // 1 is getting pushed after 2. So arriving out of order (wrt the natural order for strings)
        controller.onBarrierBlocked(mockBarrier1)

        while (!barrier1Lifted || !barrier2Lifted) { }

        verify(mockBarrier1, times(1)).lift(eq("mockResult1"))
        verify(mockBarrier2, times(1)).lift(eq("mockResult2"))
    }

    // https://github.com/udeyrishi/pipe/issues/5
    @Ignore
    @Test(expected = IllegalArgumentException::class)
    fun `checks that onBarrierLiftedAction returns correct sized lists`() {
        controller = CountedBarrierControllerImpl(capacity = 2, launchContext = DefaultTestDispatcher.createEffectiveContext()) {
            // the inputs come in sorted
            listOf("mockResult1")
        }

        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierCreated(mockBarrier1)

        controller.onBarrierBlocked(mockBarrier2)
        controller.onBarrierBlocked(mockBarrier1)

        while (!barrier1Lifted || !barrier2Lifted) { }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot register a barrier 2x`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cannot mark a barrier as blocked 2x`() {
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier1)
        controller.onBarrierBlocked(mockBarrier1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `onBarrierInterrupted checks whether the barrier was registered`() {
        controller.onBarrierInterrupted(mockBarrier1)
    }

    @Test
    fun `controller interrupts all other barriers if any one is interrupted`() {
        controller.setCapacity(3)

        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierCreated(mockBarrier2)
        controller.onBarrierCreated(mockBarrier3)

        runBlocking {
            controller.onBarrierBlocked(mockBarrier1)
            controller.onBarrierBlocked(mockBarrier2)
        }

        verify(mockBarrier1, never()).interrupt()
        verify(mockBarrier2, never()).interrupt()
        verify(mockBarrier3, never()).interrupt()

        controller.onBarrierInterrupted(mockBarrier2)

        verify(mockBarrier1).interrupt()
        verify(mockBarrier2, never()).interrupt()
        verify(mockBarrier3).interrupt()
    }

    @Test
    fun `controller interrupts any future registrations if any one is interrupted`() {
        controller.setCapacity(3)
        controller.onBarrierCreated(mockBarrier1)
        controller.onBarrierInterrupted(mockBarrier1)

        verify(mockBarrier2, never()).interrupt()
        controller.onBarrierCreated(mockBarrier2)
        verify(mockBarrier2).interrupt()

        verify(mockBarrier3, never()).interrupt()
        controller.onBarrierCreated(mockBarrier3)
        verify(mockBarrier3).interrupt()
    }
}
