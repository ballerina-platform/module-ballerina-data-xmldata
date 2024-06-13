/*
 * Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.xmldata.xml;

import io.ballerina.lib.data.xmldata.utils.Constants;
import io.ballerina.lib.data.xmldata.utils.DataUtils;
import io.ballerina.lib.data.xmldata.utils.DataUtils.XmlAnalyzerData;
import io.ballerina.lib.data.xmldata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.xmldata.utils.DiagnosticLog;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.XmlNodeType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BXml;
import io.ballerina.runtime.api.values.BXmlItem;
import io.ballerina.runtime.api.values.BXmlSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

/**
 * Convert Xml value to a ballerina record.
 *
 * @since 0.1.0
 */
public class XmlTraversal {

    private static final ThreadLocal<XmlTree> tlXmlTree = ThreadLocal.withInitial(XmlTree::new);

    public static Object traverse(BXml xml, BMap<BString, Object> options, Type type) {
        XmlTree xmlTree = tlXmlTree.get();
        return xmlTree.traverseXml(xml, options, type);
    }

    static class XmlTree {
        private Object currentNode;

        @SuppressWarnings("unchecked")
        public Object traverseXml(BXml xml, BMap<BString, Object> options, Type type) {
            Type referredType = TypeUtils.getReferredType(type);
            switch (referredType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    XmlAnalyzerData analyzerData = new XmlAnalyzerData();
                    DataUtils.updateOptions(options, analyzerData);
                    RecordType recordType = (RecordType) referredType;
                    currentNode = ValueCreator.createRecordValue(recordType.getPackage(), recordType.getName());
                    BXml nextXml = validateRootElement(xml, recordType, analyzerData);
                    Object resultRecordValue = traverseXml(nextXml, recordType, analyzerData);
                    DataUtils.validateRequiredFields(analyzerData);
                    return resultRecordValue;
                }
                case TypeTags.MAP_TAG -> {
                    MapType mapType = (MapType) referredType;
                    RecordType anonRecType = TypeCreator.createRecordType(Constants.ANON_TYPE, mapType.getPackage(), 0,
                            new HashMap<>(), mapType.getConstrainedType(), false, 0);
                    return traverseXml(xml, options, anonRecType);
                }
                default -> {
                    return DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, Constants.RECORD_OR_MAP,
                            type.getName());
                }
            }
        }

        private Object traverseXml(BXml xml, Type type, XmlAnalyzerData analyzerData) {
            switch (xml.getNodeType()) {
                case ELEMENT -> convertElement((BXmlItem) xml, analyzerData);
                case SEQUENCE -> convertSequence((BXmlSequence) xml, type, analyzerData);
                case TEXT -> convertText(xml.toString(), analyzerData);
            }
            return currentNode;
        }

        @SuppressWarnings("unchecked")
        private void convertText(String text, XmlAnalyzerData analyzerData) {
            Field currentField = analyzerData.currentField;
            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;

            String textFieldName = analyzerData.textFieldName;
            if (currentField == null) {
                QualifiedName contentQName = new QualifiedName("", textFieldName, "");
                if (analyzerData.fieldHierarchy.peek().contains(contentQName)) {
                    currentField = analyzerData.fieldHierarchy.peek().remove(contentQName);
                } else if (analyzerData.visitedFieldHierarchy.peek().contains(contentQName)) {
                    currentField = analyzerData.visitedFieldHierarchy.peek().get(contentQName);
                } else if (analyzerData.restTypes.peek() != null) {
                    currentField = TypeCreator.createField(analyzerData.restTypes.peek(),
                            analyzerData.textFieldName, SymbolFlags.REQUIRED);
                } else if (!analyzerData.allowDataProjection) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, textFieldName,
                            analyzerData.rootRecord);
                } else {
                    return;
                }
            }

            BString fieldName = StringUtils.fromString(currentField.getFieldName());
            Type fieldType = TypeUtils.getReferredType(currentField.getFieldType());

            Object convertedValue = DataUtils.convertStringToExpType(StringUtils.fromString(text), fieldType);
            Object value = mapValue.get(fieldName);
            if (value instanceof BArray) {
                if (fieldName.getValue().equals(textFieldName)) {
                    mapValue.put(fieldName, convertedValue);
                    return;
                }

                if (DataUtils.isAnydataOrJson(fieldType.getTag())) {
                    ((BArray) value).append(convertedValue);
                    return;
                }

                ArrayType arrayType = (ArrayType) fieldType;
                int currentIndex = analyzerData.arrayIndexes.peek().get(fieldName.getValue());
                if (arrayType.getState() == ArrayType.ArrayState.CLOSED && arrayType.getSize() <= currentIndex) {
                    DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(analyzerData.allowDataProjection);
                    return;
                }
                ((BArray) value).add(currentIndex, convertedValue);
            } else {
                mapValue.put(fieldName, convertedValue);
            }
        }

        @SuppressWarnings("unchecked")
        private void convertElement(BXmlItem xmlItem, XmlAnalyzerData analyzerData) {
            QualifiedName elementQName = DataUtils.getElementName(xmlItem.getQName());
            QualifiedNameMap<Field> fieldsMap = analyzerData.fieldHierarchy.peek();
            Field currentField;
            if (analyzerData.visitedFieldHierarchy.peek().contains(elementQName)) {
                currentField = analyzerData.visitedFieldHierarchy.peek().get(elementQName);
                Type fieldType = TypeUtils.getReferredType(currentField.getFieldType());
                if (!DataUtils.isArrayValueAssignable(fieldType.getTag())) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.FOUND_ARRAY_FOR_NON_ARRAY_TYPE, fieldType,
                            currentField.getFieldName());
                }
            } else {
                currentField = fieldsMap.remove(elementQName);
            }

            analyzerData.currentField = currentField;

            if (currentField == null) {
                Type restType = analyzerData.restTypes.peek();
                String elementName = elementQName.getLocalPart();
                if (restType != null) {
                    if (fieldsMap.contains(elementName)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, elementName,
                                analyzerData.rootRecord);
                    }
                    convertToRestType(xmlItem, restType, analyzerData);
                } else if (!analyzerData.allowDataProjection) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, elementName,
                            analyzerData.rootRecord);
                }
                return;
            }

            analyzerData.visitedFieldHierarchy.peek().put(elementQName, currentField);
            Type currentFieldType = currentField.getFieldType();
            if (!DataUtils.isSupportedType(currentFieldType)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, currentFieldType);
            }

            if (DataUtils.isRegExpType(currentFieldType)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE);
            }

            convertToFieldType(xmlItem, currentField, currentField.getFieldName(), currentFieldType,
                    (BMap<BString, Object>) currentNode, analyzerData);
        }

        private void convertToFieldType(BXmlItem xmlItem, Field currentField, String fieldName, Type currentFieldType,
                                        BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            switch (currentFieldType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> convertToRecordType(xmlItem, currentFieldType, fieldName,
                        (RecordType) currentFieldType, mapValue, analyzerData);
                case TypeTags.ARRAY_TAG -> convertToArrayType(xmlItem, currentField, mapValue,
                        StringUtils.fromString(fieldName), (ArrayType) currentFieldType, analyzerData);
                case TypeTags.MAP_TAG -> convertToMapType(xmlItem, currentFieldType, currentFieldType,
                        fieldName, mapValue, analyzerData);
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
                    updateNextMap(currentFieldType, analyzerData);
                    analyzerData.arrayIndexes.push(new HashMap<>());
                    convertToRestType(xmlItem, currentFieldType, analyzerData);
                    DataUtils.popExpectedTypeStacks(analyzerData);
                }
                case TypeTags.TYPE_REFERENCED_TYPE_TAG ->
                    convertToFieldType(xmlItem, currentField, fieldName, TypeUtils.getReferredType(currentFieldType),
                            mapValue, analyzerData);
                default -> traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
            }
        }

        private void convertToArrayType(BXmlItem xmlItem, Field field, BMap<BString, Object> mapValue,
                                        BString bCurrentFieldName, ArrayType arrayType, XmlAnalyzerData analyzerData) {
            Object temp = mapValue.get(bCurrentFieldName);
            Type elementType = arrayType.getElementType();

            if (DataUtils.isRegExpType(elementType)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE);
            }

            String fieldName = field.getFieldName();
            int referredElementTypeTag = TypeUtils.getReferredType(elementType).getTag();
            if (referredElementTypeTag == TypeTags.JSON_TAG || referredElementTypeTag == TypeTags.ANYDATA_TAG) {
                convertToRestType(xmlItem, arrayType, analyzerData);
                return;
            }

            if (temp == null) {
                analyzerData.arrayIndexes.peek().put(fieldName, 0);
                mapValue.put(bCurrentFieldName, DataUtils.createArrayValue(arrayType));
            } else {
                HashMap<String, Integer> indexes = analyzerData.arrayIndexes.peek();
                indexes.put(fieldName, indexes.get(fieldName) + 1);
            }

            convertToArrayMemberType(xmlItem, fieldName, arrayType, elementType, mapValue, analyzerData);
        }

        private void convertToArrayMemberType(BXmlItem xmlItem, String fieldName, ArrayType fieldType, Type elementType,
                                              BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            switch (elementType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> convertToRecordType(xmlItem, fieldType, fieldName,
                        (RecordType) elementType, mapValue, analyzerData);
                case TypeTags.MAP_TAG ->
                    convertToMapType(xmlItem, fieldType, elementType, fieldName, mapValue, analyzerData);
                case TypeTags.TYPE_REFERENCED_TYPE_TAG ->
                    convertToArrayMemberType(xmlItem, fieldName, fieldType, TypeUtils.getReferredType(elementType),
                            mapValue, analyzerData);
                default -> traverseXml(xmlItem.getChildrenSeq(), fieldType, analyzerData);
            }
        }

        private void convertToRecordType(BXmlItem xmlItem, Type currentFieldType, String fieldName,
                                         RecordType elementType, BMap<BString, Object> mapValue,
                                         XmlAnalyzerData analyzerData) {
            currentNode = updateNextRecord(xmlItem, elementType, fieldName,
                    currentFieldType, mapValue, analyzerData);
            RecordType prevRecord = analyzerData.rootRecord;
            analyzerData.rootRecord = elementType;
            traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
            DataUtils.validateRequiredFields(analyzerData);
            DataUtils.popExpectedTypeStacks(analyzerData);
            analyzerData.rootRecord = prevRecord;
            currentNode = analyzerData.nodesStack.pop();
        }

        private void convertToMapType(BXmlItem xmlItem, Type fieldType, Type elementType, String fieldName,
                                      BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            updateNextMap(elementType, analyzerData);
            currentNode = updateNextMappingValue(elementType, fieldName, fieldType, mapValue, analyzerData);
            traverseXml(xmlItem.getChildrenSeq(), fieldType, analyzerData);
            DataUtils.validateRequiredFields(analyzerData);
            DataUtils.popExpectedTypeStacks(analyzerData);
            currentNode = analyzerData.nodesStack.pop();
        }

        private void updateNextMap(Type fieldType, XmlAnalyzerData analyzerData) {
            if (fieldType.getTag() == TypeTags.MAP_TAG) {
                analyzerData.restTypes.push(((MapType) fieldType).getConstrainedType());
            } else {
                analyzerData.restTypes.push(fieldType);
            }
            analyzerData.fieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            analyzerData.visitedFieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            analyzerData.attributeHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
        }

        private BMap<BString, Object> updateNextRecord(BXmlItem xmlItem, RecordType recordType, String fieldName,
                                                       Type fieldType, BMap<BString, Object> currentMapValue,
                                                       XmlAnalyzerData analyzerData) {
            DataUtils.updateExpectedTypeStacks(recordType, analyzerData);
            BMap<BString, Object> nextValue =
                    updateNextMappingValue(recordType, fieldName, fieldType, currentMapValue, analyzerData);
            QName qName = xmlItem.getQName();
            DataUtils.validateTypeNamespace(qName.getPrefix(), qName.getNamespaceURI(), recordType);
            handleAttributes(xmlItem, nextValue, analyzerData);
            return nextValue;
        }

        private BMap<BString, Object> updateNextMappingValue(Type type, String fieldName, Type fieldType,
                                                             BMap<BString, Object> currentMapValue,
                                                             XmlAnalyzerData analyzerData) {
            analyzerData.currentField = null;

            BMap<BString, Object> nextValue;
            switch (type.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    RecordType recordType = (RecordType) type;
                    nextValue = ValueCreator.createRecordValue(recordType.getPackage(), recordType.getName());
                }
                case TypeTags.MAP_TAG -> nextValue = ValueCreator.createMapValue((MapType) type);
                case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                        nextValue = ValueCreator.createMapValue(TypeCreator.createMapType(type));
                default -> throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
            }

            Object temp = currentMapValue.get(StringUtils.fromString(fieldName));
            if (temp instanceof BArray) {
                ArrayType arrayType = (ArrayType) fieldType;
                int currentIndex = analyzerData.arrayIndexes.peek().get(fieldName);
                if (arrayType.getState() == ArrayType.ArrayState.OPEN || currentIndex < arrayType.getSize()) {
                    ((BArray) temp).add(currentIndex, nextValue);
                } else {
                    DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(analyzerData.allowDataProjection);
                }
            } else {
                currentMapValue.put(StringUtils.fromString(fieldName), nextValue);
            }
            analyzerData.nodesStack.push(currentMapValue);
            analyzerData.arrayIndexes.push(new HashMap<>());
            return nextValue;
        }

        @SuppressWarnings("unchecked")
        private void convertToRestType(BXmlItem xmlItem, Type restType, XmlAnalyzerData analyzerData) {
            String elemName = xmlItem.getQName().getLocalPart();
            analyzerData.currentField = TypeCreator.createField(restType, elemName, SymbolFlags.PUBLIC);
            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;
            checkRestTypeAndConvert(xmlItem, elemName, restType, restType, mapValue, analyzerData);
        }

        private void checkRestTypeAndConvert(BXmlItem xmlItem, String elemName, Type restType, Type elementType,
                                             BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            switch (elementType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG ->
                    convertToRecordType(xmlItem, restType, elemName, (RecordType) elementType, mapValue, analyzerData);
                case TypeTags.ARRAY_TAG -> {
                    HashMap<String, Integer> indexes = analyzerData.arrayIndexes.peek();
                    if (!indexes.containsKey(elemName)) {
                        indexes.put(elemName, 0);
                        mapValue.put(StringUtils.fromString(elemName), DataUtils.createArrayValue(restType));
                    } else {
                        indexes.put(elemName, indexes.get(elemName) + 1);
                    }

                    checkRestTypeAndConvert(xmlItem, elemName, restType, ((ArrayType) restType).getElementType(),
                            mapValue, analyzerData);
                }
                default -> {
                    BString bElementName = StringUtils.fromString(elemName);
                    if (mapValue.containsKey(bElementName) && mapValue.get(bElementName) != null) {
                        handleArrayValueForRestType(xmlItem, elemName, restType, mapValue, analyzerData);
                        return;
                    }

                    if (isNextElementContent(xmlItem)) {
                        convertContentToRestType(xmlItem, bElementName, restType, mapValue, analyzerData);
                        return;
                    }

                    if (restType.getTag() != TypeTags.ANYDATA_TAG && restType.getTag() != TypeTags.JSON_TAG) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.EXPECTED_ANYDATA_OR_JSON);
                    }

                    convertToJsonOrAnydataAsRestType(xmlItem, bElementName, restType, mapValue, analyzerData);
                }
            }
        }

        private void handleArrayValueForRestType(BXmlItem xmlItem, String elemName, Type restType,
                                                 BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            BString bElementName = StringUtils.fromString(elemName);
            Object currentElement = mapValue.get(bElementName);
            BArray arrayValue;
            if (!(currentElement instanceof BArray)) {
                if (!DataUtils.isArrayValueAssignable(restType.getTag())) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.FOUND_ARRAY_FOR_NON_ARRAY_TYPE, restType,
                            elemName);
                }
                arrayValue = DataUtils.createArrayValue(restType);
                arrayValue.append(currentElement);
                mapValue.put(bElementName, arrayValue);
            } else {
                arrayValue = (BArray) currentElement;
            }

            if (isNextElementContent(xmlItem)) {
                if (isElementHasAttributes(xmlItem)) {
                    BMap<BString, Object> nextValue =
                            ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
                    handleAttributesRest(xmlItem, nextValue, restType);
                    arrayValue.append(nextValue);

                    if (!nextValue.isEmpty()) {
                        analyzerData.currentField =
                                TypeCreator.createField(restType, analyzerData.textFieldName, SymbolFlags.REQUIRED);
                        analyzerData.nodesStack.push(currentNode);
                        currentNode = nextValue;
                        traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                        currentNode = analyzerData.nodesStack.pop();
                        return;
                    }
                }
                traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                return;
            }
            BMap<BString, Object> nextValue =
                    ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
            if (DataUtils.isAnydataOrJson(restType.getTag())) {
                arrayValue.append(nextValue);
            } else {
                ArrayType arrayType = (ArrayType) restType;
                int currentIndex = analyzerData.arrayIndexes.peek().get(elemName);
                if (arrayType.getState() == ArrayType.ArrayState.CLOSED
                        && arrayType.getSize() <= currentIndex) {
                    DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(analyzerData.allowDataProjection);
                } else {
                    arrayValue.add(currentIndex, nextValue);
                }
            }
            analyzerData.nodesStack.push(currentNode);
            currentNode = nextValue;
            handleAttributesRest(xmlItem, nextValue, restType);

            analyzerData.fieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            analyzerData.visitedFieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            analyzerData.arrayIndexes.push(new HashMap<>());
            if (restType.getTag() == TypeTags.ARRAY_TAG) {
                Type memberType = ((ArrayType) restType).getElementType();
                analyzerData.restTypes.push(memberType);
                traverseXml(xmlItem.getChildrenSeq(), memberType, analyzerData);
            } else {
                analyzerData.restTypes.push(restType);
                traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
            }
            analyzerData.fieldHierarchy.pop();
            analyzerData.visitedFieldHierarchy.pop();
            analyzerData.restTypes.pop();
            analyzerData.arrayIndexes.pop();
            currentNode = analyzerData.nodesStack.pop();
        }

        private void convertContentToRestType(BXmlItem xmlItem, BString bElementName, Type restType,
                                              BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            if (isElementHasAttributes(xmlItem)) {
                BMap<BString, Object> nextValue =
                        ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
                handleAttributesRest(xmlItem, nextValue, restType);
                mapValue.put(bElementName, nextValue);

                if (!nextValue.isEmpty()) {
                    analyzerData.currentField =
                            TypeCreator.createField(restType, analyzerData.textFieldName, SymbolFlags.REQUIRED);
                    analyzerData.nodesStack.push(currentNode);
                    currentNode = nextValue;
                    traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                    currentNode = analyzerData.nodesStack.pop();
                    return;
                }
            }
            traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
        }

        private void convertToJsonOrAnydataAsRestType(BXmlItem xmlItem, BString bElementName, Type restType,
                                                      BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            BMap<BString, Object> nextValue =
                    ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
            mapValue.put(bElementName, nextValue);
            analyzerData.nodesStack.push(currentNode);
            currentNode = nextValue;
            handleAttributesRest(xmlItem, nextValue, restType);
            traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
            currentNode = analyzerData.nodesStack.pop();
        }

        private boolean isElementHasAttributes(BXmlItem xmlItem) {
            return !xmlItem.getAttributesMap().isEmpty();
        }

        private boolean isNextElementContent(BXmlItem xml) {
            for (BXml bXml : xml.getChildrenSeq().getChildrenList()) {
                String textValue = bXml.toString();
                if (isCommentOrPi(bXml) || textValue.trim().isEmpty()) {
                    continue;
                }

                if (bXml.getNodeType() == XmlNodeType.TEXT) {
                    return true;
                }
            }
            return false;
        }

        private List<BXml> filterEmptyValuesOrCommentOrPi(List<BXml> sequence) {
            List<BXml> newSequence = new ArrayList<>();
            for (BXml value: sequence) {
                if (isCommentOrPi(value)) {
                    continue;
                }

                String textValue = value.toString();
                if (textValue.isEmpty() || !textValue.trim().isEmpty()) {
                    newSequence.add(value);
                }
            }
            return newSequence;
        }

        private void convertSequence(BXmlSequence xmlSequence, Type type, XmlAnalyzerData analyzerData) {
            List<BXml> newSequence = filterEmptyValuesOrCommentOrPi(xmlSequence.getChildrenList());

            if (newSequence.isEmpty()) {
                return;
            }

            if (newSequence.size() == 1) {
                currentNode = traverseXml(newSequence.get(0), type, analyzerData);
                return;
            }
            currentNode = convertHeterogeneousSequence(newSequence, type, analyzerData);
        }

        private Object convertHeterogeneousSequence(List<BXml> sequence, Type type, XmlAnalyzerData analyzerData) {
            if (isAllChildrenText(sequence)) {
                return handleCommentInMiddleOfText(sequence, type, analyzerData);
            }

            for (BXml bXml: sequence) {
                if (!isCommentOrPi(bXml)) {
                    traverseXml(bXml, type, analyzerData);
                }
            }
            return currentNode;
        }

        private boolean isAllChildrenText(List<BXml> sequence) {
            for (BXml bXml: sequence) {
                if (bXml.getNodeType() != XmlNodeType.TEXT) {
                    return false;
                }
            }
            return true;
        }

        private Object handleCommentInMiddleOfText(List<BXml> sequence, Type type, XmlAnalyzerData analyzerData) {
            if (!DataUtils.isStringValueAssignable(type.getTag())) {
                throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, type, PredefinedTypes.TYPE_STRING);
            }

            StringBuilder textBuilder = new StringBuilder();
            for (BXml bXml: sequence) {
                textBuilder.append(bXml.toString());
            }
            convertText(textBuilder.toString(), analyzerData);
            return currentNode;
        }

        private static boolean isCommentOrPi(BXml bxml) {
            return bxml.getNodeType() == XmlNodeType.COMMENT || bxml.getNodeType() == XmlNodeType.PI;
        }

        @SuppressWarnings("unchecked")
        private BXml validateRootElement(BXml xml, RecordType recordType, XmlAnalyzerData analyzerData) {
            if (xml.getNodeType() == XmlNodeType.SEQUENCE) {
                List<BXml> newSequence = filterEmptyValuesOrCommentOrPi(((BXmlSequence) xml).getChildrenList());
                if (newSequence.size() == 1) {
                    return validateRootElement(newSequence.get(0), recordType, analyzerData);
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.XML_ROOT_MISSING);
            } else if (xml.getNodeType() == XmlNodeType.TEXT) {
                throw DiagnosticLog.error(DiagnosticErrorCode.XML_ROOT_MISSING);
            }
            BXmlItem xmlItem = (BXmlItem) xml;
            analyzerData.rootRecord = recordType;
            QualifiedName elementQName = DataUtils.getElementName(xmlItem.getQName());
            analyzerData.rootElement =
                    DataUtils.validateAndGetXmlNameFromRecordAnnotation(recordType, recordType.getName(), elementQName);
            DataUtils.validateTypeNamespace(elementQName.getPrefix(), elementQName.getNamespaceURI(), recordType);

            // Keep track of fields and attributes
            DataUtils.updateExpectedTypeStacks(recordType, analyzerData);
            handleAttributes(xmlItem, (BMap<BString, Object>) currentNode, analyzerData);
            analyzerData.arrayIndexes.push(new HashMap<>());
            return xmlItem.getChildrenSeq();
        }

        private void handleAttributes(BXmlItem xmlItem, BMap<BString, Object> currentNode,
                                      XmlAnalyzerData analyzerData) {
            HashSet<String> innerElements = findAllInnerElement(xmlItem);
            BMap<BString, BString> attributeMap = xmlItem.getAttributesMap();
            Map<String, String> nsPrefixMap = getNamespacePrefixes(attributeMap);
            for (Map.Entry<BString, BString> entry : attributeMap.entrySet()) {
                if (isNamespacePrefixEntry(entry)) {
                    continue;
                }
                BString key = entry.getKey();
                QualifiedName attribute = getAttributePreservingNamespace(nsPrefixMap, key.getValue(),
                        analyzerData.attributePrefix);
                Field field = analyzerData.attributeHierarchy.peek().remove(attribute);
                if (field == null) {
                    if (innerElements.contains(attribute.getLocalPart())) {
                        // Element and Attribute have same name. Priority given to element.
                        continue;
                    }
                    if (analyzerData.visitedFieldHierarchy.peek().contains(attribute)) {
                        field = analyzerData.visitedFieldHierarchy.peek().get(attribute);
                    } else {
                        field = analyzerData.fieldHierarchy.peek().remove(attribute);
                        analyzerData.visitedFieldHierarchy.peek().put(attribute, field);
                    }
                }

                if (field == null) {
                    if (analyzerData.allowDataProjection) {
                        continue;
                    }
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, attribute.getLocalPart(),
                            analyzerData.rootRecord);
                }

                try {
                    currentNode.put(StringUtils.fromString(field.getFieldName()),
                            DataUtils.convertStringToExpType(attributeMap.get(key), field.getFieldType()));
                } catch (Exception e) {
                    // Ignore: Expected type will mismatch when element and attribute having same name.
                }
            }
        }

        private void handleAttributesRest(BXmlItem xmlItem, BMap<BString, Object> currentNode, Type restType) {
            HashSet<String> innerElements = findAllInnerElement(xmlItem);
            BMap<BString, BString> attributeMap = xmlItem.getAttributesMap();
            Map<String, String> nsPrefixMap = getNamespacePrefixes(attributeMap);
            for (Map.Entry<BString, BString> entry : attributeMap.entrySet()) {
                if (isNamespacePrefixEntry(entry)) {
                    continue;
                }
                BString key = entry.getKey();
                QualifiedName attribute = getAttributePreservingNamespace(nsPrefixMap, key.getValue(), "");

                if (innerElements.contains(attribute.getLocalPart())) {
                    // Element and Attribute have same name. Priority given to element.
                    continue;
                }

                try {
                    currentNode.put(StringUtils.fromString(attribute.getLocalPart()),
                            DataUtils.convertStringToExpType(attributeMap.get(key), restType));
                } catch (Exception e) {
                    // Ignore: Expected type will mismatch when element and attribute having same name.
                }
            }
        }

        private ConcurrentHashMap<String, String> getNamespacePrefixes(BMap<BString, BString> xmlAttributeMap) {
            ConcurrentHashMap<String, String> nsPrefixMap = new ConcurrentHashMap<>();
            for (Map.Entry<BString, BString> entry : xmlAttributeMap.entrySet()) {
                if (isNamespacePrefixEntry(entry)) {
                    String prefix = entry.getKey().getValue().substring(Constants.NS_PREFIX_BEGIN_INDEX);
                    String ns = entry.getValue().getValue();
                    nsPrefixMap.put(ns, prefix);
                }
            }
            return nsPrefixMap;
        }

        private boolean isNamespacePrefixEntry(Map.Entry<BString, BString> entry) {
            return entry.getKey().getValue().startsWith(BXmlItem.XMLNS_NS_URI_PREFIX);
        }

        private QualifiedName getAttributePreservingNamespace(Map<String, String> nsPrefixMap, String attributeKey,
                                                              String attributePrefix) {
            int nsEndIndex = attributeKey.lastIndexOf('}');
            if (nsEndIndex > 0) {
                String ns = attributeKey.substring(1, nsEndIndex);
                String local = attributeKey.substring(nsEndIndex + 1);
                String nsPrefix = nsPrefixMap.get(ns);
                // `!nsPrefix.equals("xmlns")` because attributes does not belong to default namespace.
                if (nsPrefix == null) {
                    nsPrefix = "";
                }
                return new QualifiedName(ns, attributePrefix + local, nsPrefix);
            }
            return new QualifiedName(attributePrefix + attributeKey);
        }

        private HashSet<String> findAllInnerElement(BXmlItem xmlItem) {
            HashSet<String> elements = new HashSet<>();
            for (BXml xmlElem : xmlItem.getChildrenSeq().getChildrenList()) {
                elements.add(xmlElem.getElementName());
            }
            return elements;
        }
    }
}
