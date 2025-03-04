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
import io.servicetalk.concurrent.api.LegacyTestCompletable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class SubscribeTest {

    private LegacyTestCompletable source;
    private Cancellable cancellable;

    @BeforeEach
    public void setUp() {
        source = new LegacyTestCompletable();
        cancellable = source.subscribe();
    }

    @Test
    public void testCancel() {
        source.verifyNotCancelled();
        cancellable.cancel();
        source.verifyCancelled();
    }
}
