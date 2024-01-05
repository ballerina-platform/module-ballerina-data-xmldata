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

import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.Future;
import io.ballerina.runtime.api.types.MethodType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.stdlib.data.xmldata.utils.DiagnosticErrorCode;
import io.ballerina.stdlib.data.xmldata.utils.DiagnosticLog;
import io.ballerina.stdlib.data.xmldata.xml.XmlParser;

import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * This class will read data from a Ballerina Stream of byte blocks, in non-blocking manner.
 *
 * @since 0.1.0
 */
public class DataReaderTask implements Runnable {

    private static final String METHOD_NAME_NEXT = "next";
    private static final String METHOD_NAME_CLOSE = "close";

    private final Environment env;
    private final BObject iteratorObj;
    private final Future future;
    private final BTypedesc typed;

    public DataReaderTask(Environment env, BObject iteratorObj, Future future, BTypedesc typed) {
        this.env = env;
        this.iteratorObj = iteratorObj;
        this.future = future;
        this.typed = typed;
    }

    static MethodType resolveNextMethod(BObject iterator) {
        MethodType method = getMethodType(iterator, METHOD_NAME_NEXT);
        if (method != null) {
            return method;
        }
        throw new IllegalStateException("next method not found in the iterator object");
    }

    static MethodType resolveCloseMethod(BObject iterator) {
        return getMethodType(iterator, METHOD_NAME_CLOSE);
    }

    private static MethodType getMethodType(BObject iterator, String methodName) {
        ObjectType objectType = (ObjectType) TypeUtils.getReferredType(iterator.getOriginalType());
        MethodType[] methods = objectType.getMethods();
        // Assumes compile-time validation of the iterator object
        for (MethodType method : methods) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    @Override
    public void run() {
        DataReaderTask.ResultConsumer<Object> resultConsumer = new DataReaderTask.ResultConsumer<>(future);
        try (var byteBlockSteam = new BallerinaByteBlockInputStream(env, iteratorObj, resolveNextMethod(iteratorObj),
                                                                    resolveCloseMethod(iteratorObj), resultConsumer)) {
            Object result = XmlParser.parse(new InputStreamReader(byteBlockSteam), typed.getDescribingType());
            future.complete(result);
        } catch (Exception e) {
            future.complete(DiagnosticLog.error(DiagnosticErrorCode.STREAM_BROKEN, e.getMessage()));
        }
    }

    /**
     * This class will hold module related utility functions.
     *
     * @param <T>    The type of the result
     * @param future The future to complete
     * @since 0.1.0
     */
    public record ResultConsumer<T>(Future future) implements Consumer<T> {

        @Override
        public void accept(T t) {
            future.complete(t);
        }
    }
}
