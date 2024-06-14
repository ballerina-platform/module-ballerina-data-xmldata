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
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
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

import static io.ballerina.lib.data.xmldata.utils.Constants.ENABLE_CONSTRAINT_VALIDATION;

/**
 * Convert Xml value to a ballerina record.
 *
 * @since 0.1.0
 */
public class XmlTraversal {

    private static final ThreadLocal<XmlTree> tlXmlTree = ThreadLocal.withInitial(XmlTree::new);

    public static Object traverse(BXml xml, BMap<BString, Object> options, BTypedesc typed) {
        Object convertedValue = traverse(xml, options, typed.getDescribingType());
        if (convertedValue instanceof BError) {
            return convertedValue;
        }
        return DataUtils.validateConstraints(convertedValue, typed,
                (Boolean) options.get(ENABLE_CONSTRAINT_VALIDATION));
    }

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
                    currentNode = ValueCreator.createRecordValue(recordType);
                    BXml nextXml = validateRootElement(xml, recordType, analyzerData);
                    Object resultRecordValue = traverseXml(nextXml, recordType, analyzerData);
                    DataUtils.validateRequiredFields((BMap<BString, Object>) resultRecordValue, analyzerData);
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
            if (mapValue.containsKey(fieldName)) {
                if (fieldName.getValue().equals(textFieldName)) {
                    mapValue.put(fieldName, convertedValue);
                    return;
                }
                if (!DataUtils.isArrayValueAssignable(fieldType.getTag())) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.FOUND_ARRAY_FOR_NON_ARRAY_TYPE, fieldType, fieldName);
                }

                Object value = mapValue.get(fieldName);
                int arraySize = (DataUtils.getValidArrayType(fieldType)).getSize();
                if (value instanceof BArray) {
                    if (arraySize != -1 && arraySize <= ((BArray) value).getLength()) {
                        DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(analyzerData.allowDataProjection);
                        return;
                    }
                    ((BArray) value).append(convertedValue);
                } else {
                    BArray array = DataUtils.createNewAnydataList(fieldType);
                    array.append(value);
                    array.append(convertedValue);
                    mapValue.put(fieldName, array);
                }
            } else {
                mapValue.put(fieldName, convertedValue);
            }
        }

        @SuppressWarnings("unchecked")
        private void convertElement(BXmlItem xmlItem, XmlAnalyzerData analyzerData) {
            QualifiedName elementQName = DataUtils.getElementName(xmlItem.getQName());
            QualifiedNameMap<Field> fieldsMap = analyzerData.fieldHierarchy.peek();
            Field currentField = fieldsMap.get(elementQName);
            analyzerData.currentField = currentField;

            if (currentField == null) {
                Type restType = analyzerData.restTypes.peek();
                String elementName = elementQName.getLocalPart();
                if (restType != null) {
                    if (fieldsMap.contains(elementName)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, elementName,
                                analyzerData.rootRecord);
                    }
                    convertWithRestType(xmlItem, restType, analyzerData);
                } else if (!analyzerData.allowDataProjection) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, elementName,
                            analyzerData.rootRecord);
                }
                return;
            }

            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;
            Type currentFieldType = TypeUtils.getReferredType(currentField.getFieldType());
            if (!DataUtils.isSupportedType(currentFieldType)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, currentFieldType);
            }

            String fieldName = currentField.getFieldName();
            BString bCurrentFieldName = StringUtils.fromString(fieldName);
            switch (currentFieldType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    handleRecordType(xmlItem, currentFieldType, fieldName, (RecordType) currentFieldType, mapValue,
                            analyzerData);
                    return;
                }
                case TypeTags.ARRAY_TAG -> {
                    if (!mapValue.containsKey(bCurrentFieldName)) {
                        BArray array = DataUtils.createNewAnydataList(currentFieldType);
                        mapValue.put(bCurrentFieldName, array);
                    }
                    Type elementType = TypeUtils.getReferredType(((ArrayType) currentFieldType).getElementType());
                    int elementTypeTag = elementType.getTag();
                    if (elementTypeTag == TypeTags.RECORD_TYPE_TAG) {
                        handleRecordType(xmlItem, currentFieldType, fieldName, (RecordType) elementType, mapValue,
                                analyzerData);
                        return;
                    } else if (elementTypeTag == TypeTags.MAP_TAG) {
                        updateNextMap(elementType, analyzerData);
                        currentNode = updateNextValue(elementType, fieldName, currentFieldType, mapValue,
                                analyzerData);
                        traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
                        DataUtils.validateRequiredFields((BMap<BString, Object>) currentNode, analyzerData);
                        DataUtils.removeExpectedTypeStacks(analyzerData);
                        currentNode = analyzerData.nodesStack.pop();
                        return;
                    } else if (elementTypeTag == TypeTags.JSON_TAG || elementTypeTag == TypeTags.ANYDATA_TAG) {
                        updateNextMap(elementType, analyzerData);
                        convertWithRestType(xmlItem, elementType, analyzerData);
                        DataUtils.removeExpectedTypeStacks(analyzerData);
                        return;
                    }
                }
                case TypeTags.MAP_TAG -> {
                    updateNextMap(currentFieldType, analyzerData);
                    currentNode = updateNextValue(currentFieldType, fieldName, currentFieldType, mapValue,
                            analyzerData);
                    traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
                    DataUtils.validateRequiredFields((BMap<BString, Object>) currentNode, analyzerData);
                    DataUtils.removeExpectedTypeStacks(analyzerData);
                    currentNode = analyzerData.nodesStack.pop();
                    return;
                }
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
                    updateNextMap(currentFieldType, analyzerData);
                    convertWithRestType(xmlItem, currentFieldType, analyzerData);
                    DataUtils.removeExpectedTypeStacks(analyzerData);
                    return;
                }
            }
            traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
        }

        private void handleRecordType(BXmlItem xmlItem, Type currentFieldType, String fieldName, RecordType elementType,
                                      BMap<BString, Object> mapValue, XmlAnalyzerData analyzerData) {
            currentNode = updateNextRecord(xmlItem, elementType, fieldName,
                    currentFieldType, mapValue, analyzerData);
            RecordType prevRecord = analyzerData.rootRecord;
            analyzerData.rootRecord = elementType;
            traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
            DataUtils.validateRequiredFields((BMap<BString, Object>) currentNode, analyzerData);
            DataUtils.removeExpectedTypeStacks(analyzerData);
            analyzerData.rootRecord = prevRecord;
            currentNode = analyzerData.nodesStack.pop();
        }

        private void updateNextMap(Type fieldType, XmlAnalyzerData analyzerData) {
            if (fieldType.getTag() == TypeTags.MAP_TAG) {
                analyzerData.restTypes.push(((MapType) fieldType).getConstrainedType());
            } else {
                analyzerData.restTypes.push(fieldType);
            }
            analyzerData.fieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            analyzerData.attributeHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
        }

        private BMap<BString, Object> updateNextRecord(BXmlItem xmlItem, RecordType recordType, String fieldName,
                                                       Type fieldType, BMap<BString, Object> currentMapValue,
                                                       XmlAnalyzerData analyzerData) {
            DataUtils.updateExpectedTypeStacks(recordType, analyzerData);
            BMap<BString, Object> nextValue =
                    updateNextValue(recordType, fieldName, fieldType, currentMapValue, analyzerData);
            QName qName = xmlItem.getQName();
            DataUtils.validateTypeNamespace(qName.getPrefix(), qName.getNamespaceURI(), recordType);
            handleAttributes(xmlItem, nextValue, analyzerData);
            return nextValue;
        }

        private BMap<BString, Object> updateNextValue(Type type, String fieldName, Type fieldType,
                                       BMap<BString, Object> currentMapValue, XmlAnalyzerData analyzerData) {
            analyzerData.currentField = null;

            BMap<BString, Object> nextValue;
            switch (type.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> nextValue = ValueCreator.createRecordValue((RecordType) type);
                case TypeTags.MAP_TAG -> nextValue = ValueCreator.createMapValue((MapType) type);
                case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                        nextValue = ValueCreator.createMapValue(TypeCreator.createMapType(type));
                default -> throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
            }

            Object temp = currentMapValue.get(StringUtils.fromString(fieldName));
            if (temp instanceof BArray) {
                int arraySize = ((ArrayType) fieldType).getSize();
                if (arraySize > ((BArray) temp).getLength() || arraySize == -1) {
                    ((BArray) temp).append(nextValue);
                } else {
                    DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(analyzerData.allowDataProjection);
                }
            } else {
                currentMapValue.put(StringUtils.fromString(fieldName), nextValue);
            }
            analyzerData.nodesStack.push(currentMapValue);
            return nextValue;
        }

        @SuppressWarnings("unchecked")
        private void convertWithRestType(BXmlItem xmlItem, Type restType, XmlAnalyzerData analyzerData) {
            String elemName = xmlItem.getQName().getLocalPart();
            analyzerData.currentField = TypeCreator.createField(restType, elemName, SymbolFlags.PUBLIC);
            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;

            switch (restType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    handleRecordType(xmlItem, restType, elemName, (RecordType) restType, mapValue, analyzerData);
                    return;
                }
                case TypeTags.ARRAY_TAG -> {
                    BString bElementName = StringUtils.fromString(elemName);
                    if (!mapValue.containsKey(bElementName)) {
                        BArray array = DataUtils.createNewAnydataList(restType);
                        mapValue.put(bElementName, array);
                    }
                    Type elementType = ((ArrayType) restType).getElementType();
                    if (elementType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                        handleRecordType(xmlItem, restType, elemName, (RecordType) elementType, mapValue,
                                analyzerData);
                        return;
                    }
                }
            }

            BString bElementName = StringUtils.fromString(elemName);
            BArray arrayValue;
            if (mapValue.containsKey(bElementName)) {
                Object currentElement = mapValue.get(bElementName);
                if (!(currentElement instanceof BArray)) {
                    arrayValue = DataUtils.createNewAnydataList(restType);
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
                arrayValue.append(nextValue);
                analyzerData.nodesStack.push(currentNode);
                currentNode = nextValue;
                handleAttributesRest(xmlItem, nextValue, restType);

                analyzerData.fieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
                if (restType.getTag() == TypeTags.ARRAY_TAG) {
                    Type memberType = ((ArrayType) restType).getElementType();
                    analyzerData.restTypes.push(memberType);
                    traverseXml(xmlItem.getChildrenSeq(), memberType, analyzerData);
                } else {
                    analyzerData.restTypes.push(restType);
                    traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                }
                analyzerData.fieldHierarchy.pop();
                analyzerData.restTypes.pop();
                currentNode = analyzerData.nodesStack.pop();
                return;
            }

            if (isNextElementContent(xmlItem)) {
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
                return;
            } else if (restType.getTag() != TypeTags.ANYDATA_TAG && restType.getTag() != TypeTags.JSON_TAG) {
                throw DiagnosticLog.error(DiagnosticErrorCode.EXPECTED_ANYDATA_OR_JSON);
            }
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
                    field = analyzerData.fieldHierarchy.peek().get(attribute);
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
