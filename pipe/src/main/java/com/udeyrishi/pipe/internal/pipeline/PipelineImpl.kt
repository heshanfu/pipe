/**
 * Copyright (c) 2018 Udey Rishi. All rights reserved.
 */
package com.udeyrishi.pipe.internal.pipeline

import com.udeyrishi.pipe.CountedBarrierController
import com.udeyrishi.pipe.Job
import com.udeyrishi.pipe.ManualBarrierController
import com.udeyrishi.pipe.Pipeline
import com.udeyrishi.pipe.Step
import com.udeyrishi.pipe.internal.Orchestrator
import com.udeyrishi.pipe.internal.barrier.BarrierImpl
import com.udeyrishi.pipe.internal.barrier.CountedBarrierControllerImpl
import com.udeyrishi.pipe.internal.barrier.ManualBarrierControllerImpl
import com.udeyrishi.pipe.internal.steps.StepDescriptor
import com.udeyrishi.pipe.internal.util.repeatUntilSucceeds
import com.udeyrishi.pipe.repository.DuplicateUUIDException
import com.udeyrishi.pipe.repository.MutableRepository
import com.udeyrishi.pipe.util.Logger
import java.util.UUID
import kotlin.coroutines.CoroutineContext

internal class PipelineImpl<T : Any>(private val repository: MutableRepository<in Job<T>>, private val operations: List<PipelineOperationSpec<T>>, private val launchContext: CoroutineContext, private val logger: Logger?) : Pipeline<T> {
    private val barrierControllers by lazy {
        operations
                .asSequence()
                .filterIsInstance<PipelineOperationSpec.Barrier<T>>()
                .map {
                    when (it) {
                        is PipelineOperationSpec.Barrier.Manual<T> -> ManualBarrierControllerImpl<Passenger<T>>(launchContext)
                        is PipelineOperationSpec.Barrier.Counted<T> -> CountedBarrierControllerImpl(capacity = it.capacity, onBarrierLiftedAction = it.onBarrierLiftedAction?.toPassengerStep(), launchContext = launchContext)
                    }
                }
                .toList()
    }

    override val manualBarriers by lazy {
        barrierControllers.filterIsInstance<ManualBarrierController>()
    }

    override val countedBarriers by lazy {
        barrierControllers.filterIsInstance<CountedBarrierController>()
    }

    private val countedBarrierCapacityLock = Any()

    override fun push(input: T, tag: String?): Job<T> {
        val steps = materializeSteps()
        val createdAt = System.currentTimeMillis()

        // May throw if UUID was already taken. Super rare that UUID.randomUUID() repeats UUIDs,
        // but if it still happens, try again.
        return repeatUntilSucceeds<DuplicateUUIDException, Job<T>> {
            val passenger = Passenger(input, UUID.randomUUID(), createdAt)
            val orchestrator = Orchestrator(passenger, steps, launchContext, ::onStepFailed).apply {
                this@apply.logger = this@PipelineImpl.logger
            }
            val job = Job(orchestrator)
            repository.add(tag, job)
            orchestrator.start()
            job
        }
    }

    private fun onStepFailed(failureCause: Orchestrator.StepOutOfAttemptsException) {
        synchronized(countedBarrierCapacityLock) {
            barrierControllers
                    .asSequence()
                    .filterIsInstance<CountedBarrierControllerImpl<Passenger<T>>>()
                    .filter {
                        // Do not bother the counted barriers that have reached their capacity, and hence have been lifted.
                        it.arrivalCount < it.getCapacity()
                    }
                    .filterNot {
                        // If the failure is originating from a failed counted barrier controller, do not inform the same barrier again.
                        // This prevents an infinite loop of sorts.
                        it === ((failureCause.cause as? Orchestrator.StepFailureException)?.cause as? CountedBarrierControllerImpl.BarrierLiftedActionException)?.source
                    }
                    .forEach {
                        // This will notify them to not bother waiting. The failed job is never going to arrive.
                        it.notifyError()
                    }
        }
    }

    private fun materializeSteps(): Iterator<StepDescriptor<Passenger<T>>> {
        var barrierIndex = 0

        return operations.map { spec ->
            when (spec) {
                is PipelineOperationSpec.RegularStep<T> -> StepDescriptor(spec.name, spec.attempts) {
                    it.copy(data = spec.step(it.data))
                }
                is PipelineOperationSpec.Barrier<T> -> StepDescriptor(spec.name, spec.attempts, BarrierImpl(barrierControllers[barrierIndex++]))
            }
        }.iterator()
    }
}

private fun <T : Any> Step<List<T>>.toPassengerStep(): Step<List<Passenger<T>>> {
    return { passengers ->
        passengers
                .map { it.data } // Extract data from input passengers
                .let { this(it) } // Call the original step with the list of data
                .zip(passengers) // Create tuples of (result, input passenger)
                .map { (result, inputPassenger) -> inputPassenger.copy(data = result) } // Return a new passenger with data set to the result
    }
}
