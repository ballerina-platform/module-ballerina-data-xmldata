/*
 *  Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 *  WSO2 LLC. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.stdlib.data.xmldata.io;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Thread pool for data reader.
 *
 * @since 0.1.0
 */
public class DataReaderThreadPool {

    // TODO : Make this configurable, in Ballerina Library.
    public static final ExecutorService EXECUTOR_SERVICE = new ThreadPoolExecutor(0, 20, 60L, TimeUnit.SECONDS,
                                                                                  new SynchronousQueue<>(),
                                                                                  new DataThreadFactory());

    /**
     * Thread factory for data reader.
     */
    static class DataThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread balThread = new Thread(runnable);
            balThread.setName("bal-data-xmldata-thread");
            return balThread;
        }
    }
}
