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
package io.servicetalk.concurrent.api.completable;

import io.servicetalk.concurrent.Cancellable;
import io.servicetalk.concurrent.CompletableSource.Subscriber;
import io.servicetalk.concurrent.api.Completable;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static io.servicetalk.concurrent.api.SourceAdapters.toSource;
import static io.servicetalk.concurrent.internal.DeliberateException.DELIBERATE_EXCEPTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

public class BeforeSubscribeTest extends AbstractWhenOnSubscribeTest {

    @Test
    public void testCallbackThrowsError() {
        List<Throwable> failures = new ArrayList<>();
        toSource(doSubscribe(Completable.completed(), s -> {
            throw DELIBERATE_EXCEPTION;
        })).subscribe(new Subscriber() {
            @Override
            public void onSubscribe(final Cancellable c) {
                failures.add(new AssertionError("onSubscribe invoked unexpectedly."));
            }

            @Override
            public void onError(final Throwable t) {
                failures.add(t);
            }

            @Override
            public void onComplete() {
                failures.add(new AssertionError("onComplete invoked unexpectedly."));
            }
        });
        assertThat("Unexpected errors: " + failures, failures, contains(DELIBERATE_EXCEPTION));
    }

    @Override
    protected Completable doSubscribe(Completable completable, Consumer<Cancellable> consumer) {
        return completable.beforeOnSubscribe(consumer);
    }
}
