/*
 * Copyright © 2018, 2021 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.concurrent.internal;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.SingleSource.Subscriber;
import io.servicetalk.concurrent.api.Executor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javax.annotation.Nullable;

import static io.servicetalk.concurrent.api.Executors.from;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static io.servicetalk.concurrent.internal.NoopRunnable.NOOP_RUNNABLE;
import static io.servicetalk.concurrent.internal.ThrowingRunnable.THROWING_RUNNABLE;
import static java.lang.Thread.currentThread;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class SignalOffloaderSingleTest {

    private enum OffloaderTestParam {
        THREAD_BASED {
            @Override
            OffloaderHolder get() {
                return new OffloaderHolder(ThreadBasedSignalOffloader::new);
            }

            @Override
            boolean isSupportsTermination() {
                return true;
            }
        },
        TASK_BASED {
            @Override
            OffloaderHolder get() {
                return new OffloaderHolder(TaskBasedSignalOffloader::new);
            }

            @Override
            boolean isSupportsTermination() {
                return false;
            }
        };

        abstract OffloaderHolder get();

        abstract boolean isSupportsTermination();
    }

    @Nullable
    private OffloaderHolder state;

    private void init(OffloaderTestParam offloader) {
        this.state = offloader.get();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (state != null) {
            state.shutdown();
            state = null;
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void offloadingSubscriberShouldNotOffloadCancellable(OffloaderTestParam offloader)
            throws Exception {
        init(offloader);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(offloadedSubscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyCancelNotOffloaded(state.cancellable)
                .verifyOnSuccessOffloaded(offloaded)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void offloadingCancellableShouldNotOffloadSubscriber(OffloaderTestParam offloader)
            throws Exception {
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadCancellable(state.subscriber);

        Cancellable received = state.sendOnSubscribe(offloaded);
        state.verifyOnSubscribeNotOffloaded(offloaded)
                .verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void offloadSubscriber(OffloaderTestParam offloader) throws Exception {
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnSuccessOffloaded(offloaded)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void cancelShouldTerminateOffloadingWithMultipleEntities(OffloaderTestParam offloader)
            throws Exception {
        init(offloader);
        Subscriber<? super String> offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        Cancellable received = state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyCancelOffloaded(received)
                .awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void onErrorShouldTerminateOffloadingWithMultipleEntities(OffloaderTestParam offloader)
            throws Exception {
        init(offloader);
        Subscriber<? super String> offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnErrorOffloaded(offloadedSubscriber).awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void onSuccessShouldTerminateOffloadingWithMultipleEntities(OffloaderTestParam offloader)
            throws Exception {
        init(offloader);
        Subscriber<? super String> offloadedCancellable = state.offloader.offloadCancellable(state.subscriber);
        Subscriber<? super String> offloadedSubscriber = state.offloader.offloadSubscriber(offloadedCancellable);
        state.verifyOnSubscribeOffloaded(offloadedSubscriber);
        state.verifyOnSuccessOffloaded(offloadedSubscriber).awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void onSuccessSupportsNull(OffloaderTestParam offloader) throws Exception {
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnSuccessOffloaded(offloaded, null).awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void onSubscribeThrows(OffloaderTestParam offloader) throws Exception {
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloadedWhenThrows(offloaded);
        state.awaitTermination();
        verify(state.cancellable).cancel();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void onSuccessThrows(OffloaderTestParam offloader) throws Exception {
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnSuccessOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void onErrorThrows(OffloaderTestParam offloader) throws Exception {
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloadedWhenThrows(offloaded);
        state.awaitTermination();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void offloadPostTermination(OffloaderTestParam offloader) throws Exception {
        assumeTrue(offloader.isSupportsTermination(), "Termination test not supported by this offloader.");
        init(offloader);
        Subscriber<? super String> offloaded = state.offloader.offloadSubscriber(state.subscriber);
        state.verifyOnSubscribeOffloaded(offloaded);
        state.verifyOnErrorOffloaded(offloaded);
        state.awaitTermination();
        assertThrows(IllegalStateException.class, () -> state.offloader.offloadSubscriber(state.subscriber));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {arguments}")
    @EnumSource(OffloaderTestParam.class)
    public void executorRejectsForHandleSubscribe(OffloaderTestParam offloader) {
        init(offloader);
        ThreadBasedSignalOffloader rejectOffloader = new ThreadBasedSignalOffloader(from(task -> {
            throw new RejectedExecutionException();
        }));
        rejectOffloader.offloadSubscribe(state.subscriber, __ -> {
        });
        verify(state.subscriber).onSubscribe(any(Cancellable.class));
        ArgumentCaptor<Throwable> errorCaptor = ArgumentCaptor.forClass(Throwable.class);
        verify(state.subscriber).onError(errorCaptor.capture());
        assertThat("Unexpected error received by the subscriber.", errorCaptor.getValue(),
                instanceOf(RejectedExecutionException.class));
    }

    private static final class OffloaderHolder {

        private final Executor executor;
        private final SignalOffloader offloader;
        private final Cancellable cancellable;
        private final Subscriber<String> subscriber;

        OffloaderHolder(Function<Executor, SignalOffloader> offloaderFactory) {
            executor = from(java.util.concurrent.Executors.newSingleThreadExecutor());
            offloader = offloaderFactory.apply(executor);
            cancellable = mock(Cancellable.class);
            subscriber = mockSubscriber();
        }

        void shutdown() throws Exception {
            executor.closeAsync().toFuture().get();
        }

        void awaitTermination() throws Exception {
            // Submit a task, since we use a single thread executor, this means all previous tasks have been
            // completed.
            executor.submit(() -> { }).toFuture().get();
        }

        Cancellable sendOnSubscribe(Subscriber<? super String> offloaded) {
            offloaded.onSubscribe(cancellable);
            Cancellable received = captureReceivedCancellable();
            assertThat("Unexpected subscription received.", received, is(notNullValue()));
            return received;
        }

        OffloaderHolder verifyOnSubscribeNotOffloaded(Subscriber<? super String> offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        OffloaderHolder verifyCancelNotOffloaded(Cancellable received) throws Exception {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    sameInstance(currentThread()));
            return this;
        }

        Cancellable verifyOnSubscribeOffloaded(Subscriber<? super String> offloaded) throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return captureReceivedCancellable();
        }

        void verifyOnSubscribeOffloadedWhenThrows(Subscriber<? super String> offloaded)
                throws Exception {
            final Thread invoker = sendOnSubscribeAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            captureReceivedCancellable();
        }

        OffloaderHolder verifyOnErrorOffloaded(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        void verifyOnErrorOffloadedWhenThrows(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnErrorAndReturnCaller(offloaded, THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
        }

        OffloaderHolder verifyOnSuccessOffloaded(Subscriber<? super String> offloaded) throws Exception {
            return verifyOnSuccessOffloaded(offloaded, "Hello");
        }

        OffloaderHolder verifyOnSuccessOffloadedWhenThrows(Subscriber<? super String> offloaded) throws Exception {
            Thread invoker = sendOnSuccessAndReturnCaller(offloaded, "Hello", THROWING_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyOnSuccessOffloaded(Subscriber<? super String> offloaded, @Nullable String item)
                throws Exception {
            Thread invoker = sendOnSuccessAndReturnCaller(offloaded, item, NOOP_RUNNABLE);
            assertThat("Unexpected thread invoked offloaded Subscriber.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        OffloaderHolder verifyCancelOffloaded(Cancellable received) throws Exception {
            Thread invoker = sendCancelAndReturnCaller(received);
            assertThat("Unexpected thread invoked offloaded Subscription.", invoker,
                    not(sameInstance(currentThread())));
            return this;
        }

        private Cancellable captureReceivedCancellable() {
            ArgumentCaptor<Cancellable> captor = forClass(Cancellable.class);
            verify(subscriber).onSubscribe(captor.capture());
            return captor.getValue();
        }

        private Thread sendCancelAndReturnCaller(Cancellable received) throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> invoker = new AtomicReference<>();
            doAnswer(invocation -> {
                invoker.set(currentThread());
                latch.countDown();
                return null;
            }).when(cancellable).cancel();
            received.cancel();
            latch.await();
            return invoker.get();
        }

        private Thread sendOnSubscribeAndReturnCaller(Subscriber<? super String> offloaded, Runnable doOnSubscribe)
                throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnSubscribe.run();
                return null;
            }).when(subscriber).onSubscribe(any(Cancellable.class));
            offloaded.onSubscribe(cancellable);
            latch.await();
            return subscriberInvoker.get();
        }

        private Thread sendOnErrorAndReturnCaller(Subscriber<? super String> offloaded, Runnable doOnError)
                throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnError.run();
                return null;
            }).when(subscriber).onError(any(Throwable.class));
            offloaded.onError(DELIBERATE_EXCEPTION);
            latch.await();
            return subscriberInvoker.get();
        }

        private Thread sendOnSuccessAndReturnCaller(Subscriber<? super String> offloaded, @Nullable String item,
                                                    Runnable doOnComplete) throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<Thread> subscriberInvoker = new AtomicReference<>();
            doAnswer(invocation -> {
                subscriberInvoker.set(currentThread());
                latch.countDown();
                doOnComplete.run();
                return null;
            }).when(subscriber).onSuccess(item);
            offloaded.onSuccess(item);
            latch.await();
            return subscriberInvoker.get();
        }

        @SuppressWarnings("unchecked")
        private Subscriber<String> mockSubscriber() {
            return mock(Subscriber.class);
        }
    }
}
