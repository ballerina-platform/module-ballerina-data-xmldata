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
package io.ballerina.stdlib.data.xml;

import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
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
import io.ballerina.stdlib.data.utils.Constants;
import io.ballerina.stdlib.data.utils.DataUtils;
import io.ballerina.stdlib.data.utils.DataUtils.XmlAnalyzerData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.namespace.QName;

import static io.ballerina.runtime.api.utils.StringUtils.fromString;

/**
 * Convert Xml value to a ballerina record.
 *
 * @since 0.1.0
 */

public class XmlTraversal {

    private static final ThreadLocal<XmlTree> tlXmlTree = ThreadLocal.withInitial(XmlTree::new);

    public static Object traverse(BXml xml, Type type) {
        XmlTree xmlTree = tlXmlTree.get();
        return xmlTree.traverseXml(xml, type);
    }

    static class XmlTree {
        private Object currentNode;

        public Object traverseXml(BXml xml, Type type) {
            XmlAnalyzerData analyzerData = new XmlAnalyzerData();
            Type referredType = TypeUtils.getReferredType(type);
            switch (referredType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG:
                    RecordType recordType = (RecordType) referredType;
                    currentNode = ValueCreator.createRecordValue(recordType);
                    BXml nextXml = validateRootElement(xml, recordType, analyzerData);
                    Object resultRecordValue = traverseXml(nextXml, recordType, analyzerData);
                    DataUtils.validateRequiredFields((BMap<BString, Object>) resultRecordValue, analyzerData);
                    return resultRecordValue;
                case TypeTags.MAP_TAG:
                    // TODO: Need to handle this.
                    return DataUtils.getXmlError("handle this: '" + type.getName() + "'");
                default:
                    return DataUtils.getXmlError("unsupported type: '" + type.getName() + "'");
            }
        }

        private Object traverseXml(BXml xml, Type type, XmlAnalyzerData analyzerData) {
            switch (xml.getNodeType()) {
                case ELEMENT:
                    convertElement((BXmlItem) xml, type, analyzerData);
                    break;
                case SEQUENCE:
                    convertSequence((BXmlSequence) xml, type, analyzerData);
                    break;
                case TEXT:
                    convertText(xml.toString(), analyzerData);
                    break;
            }
            return currentNode;
        }

        private void convertText(String text, XmlAnalyzerData analyzerData) {
            Field currentField = analyzerData.currentField;

            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;

            if (currentField == null) {
                Map<String, Field> currentFieldMap = analyzerData.fieldHierarchy.peek();
                if (currentFieldMap.containsKey(Constants.CONTENT)) {
                    currentField = currentFieldMap.remove(Constants.CONTENT);
                } else if (analyzerData.restTypes.peek() != null) {
                    currentField = Constants.CONTENT_FIELD;
                } else {
                    return;
                }
            }

            BString fieldName = fromString(currentField.getFieldName());
            Type fieldType = currentField.getFieldType();

            Object convertedValue = DataUtils.convertStringToExpType(fromString(text), fieldType);
            if (mapValue.containsKey(fieldName)) {
                if (!DataUtils.isArrayValueAssignable(fieldType.getTag())) {
                    throw DataUtils.getXmlError("Expected an '" + fieldType + "' value for the field '" + fieldName
                            + "' found 'array' value");
                }

                Object value = mapValue.get(fieldName);
                int arraySize = ((ArrayType) fieldType).getSize();
                if (value instanceof BArray) {
                    if (arraySize != -1 && arraySize <= ((BArray) value).getLength()) {
                        return;
                    }
                    ((BArray) value).append(convertedValue);
                } else {
                    BArray array = DataUtils.createNewAnydataList();
                    array.append(convertedValue);
                    mapValue.put(fieldName, array);
                }
            } else {
                mapValue.put(fieldName, convertedValue);
            }
        }

        private void convertElement(BXmlItem xmlItem, Type type, XmlAnalyzerData analyzerData) {
            String elementName = DataUtils.getElementName(xmlItem.getQName());
            Field currentField = analyzerData.fieldHierarchy.peek().get(elementName);
            analyzerData.currentField = currentField;

            if (currentField == null) {
                Type restType = analyzerData.restTypes.peek();
                if (restType == null) {
                    return;
                }
                convertToRestType(xmlItem, restType, analyzerData);
                return;
            }

            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;
            Type currentFieldType = TypeUtils.getReferredType(currentField.getFieldType());
            String fieldName = currentField.getFieldName();
            BString bCurrentFieldName = fromString(fieldName);

            if (type instanceof RecordType) {
                DataUtils.validateFieldNamespace(xmlItem.getQName().getPrefix(), xmlItem.getQName().getNamespaceURI(),
                        fieldName, (RecordType) type);
            }

            if (currentFieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                currentNode = updateNextRecord(xmlItem, (RecordType) currentFieldType, fieldName,
                        currentFieldType, mapValue, analyzerData);
                traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
                DataUtils.validateRequiredFields((BMap<BString, Object>) currentNode, analyzerData);
                analyzerData.fieldHierarchy.pop();
                analyzerData.restTypes.pop();
                currentNode = analyzerData.nodesStack.pop();
                return;
            } else if (currentFieldType.getTag() == TypeTags.ARRAY_TAG) {

                if (!mapValue.containsKey(bCurrentFieldName)) {
                    BArray array = DataUtils.createNewAnydataList();
                    mapValue.put(bCurrentFieldName, array);
                }
                Type elementType = TypeUtils.getReferredType(((ArrayType) currentFieldType).getElementType());
                if (elementType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    currentNode = updateNextRecord(xmlItem, (RecordType) elementType, fieldName,
                            currentFieldType, mapValue, analyzerData);
                    traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
                    DataUtils.validateRequiredFields((BMap<BString, Object>) currentNode, analyzerData);
                    analyzerData.fieldHierarchy.pop();
                    analyzerData.restTypes.pop();
                    currentNode = analyzerData.nodesStack.pop();
                    return;
                }
            }
            traverseXml(xmlItem.getChildrenSeq(), currentFieldType, analyzerData);
        }

        private BMap<BString, Object> updateNextRecord(BXmlItem xmlItem, RecordType recordType, String fieldName,
                                                       Type fieldType, BMap<BString, Object> currentMapValue,
                                                       XmlAnalyzerData analyzerData) {
            analyzerData.attributeHierarchy.push(DataUtils.getAllAttributesInRecordType(recordType));
            analyzerData.fieldHierarchy.push(DataUtils.getAllFieldsInRecordType(recordType, analyzerData));
            analyzerData.restTypes.push(recordType.getRestFieldType());
            BMap<BString, Object> nextValue =
                    updateNextValue(recordType, fieldName, fieldType, currentMapValue, analyzerData);
            QName qName = xmlItem.getQName();
            DataUtils.validateTypeNamespace(qName.getPrefix(), qName.getNamespaceURI(), recordType);
            handleAttributes(xmlItem, nextValue, recordType, analyzerData);
            return nextValue;
        }

        private BMap<BString, Object> updateNextValue(RecordType recordType, String fieldName, Type fieldType,
                                       BMap<BString, Object> currentMapValue, XmlAnalyzerData analyzerData) {
            analyzerData.currentField = null;
            BMap<BString, Object> nextValue = ValueCreator.createRecordValue(recordType);

            Object temp = currentMapValue.get(StringUtils.fromString(fieldName));
            if (temp instanceof BArray) {
                int arraySize = ((ArrayType) fieldType).getSize();
                if (arraySize > ((BArray) temp).getLength() || arraySize == -1) {
                    ((BArray) temp).append(nextValue);
                }
            } else {
                currentMapValue.put(StringUtils.fromString(fieldName), nextValue);
            }
            analyzerData.nodesStack.push(currentMapValue);
            return nextValue;
        }

        private void convertToRestType(BXmlItem xmlItem, Type restType, XmlAnalyzerData analyzerData) {
            String elemName = xmlItem.getQName().getLocalPart();
            analyzerData.currentField = TypeCreator.createField(restType, elemName, SymbolFlags.PUBLIC);
            BMap<BString, Object> mapValue = (BMap<BString, Object>) currentNode;

            if (restType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                RecordType recordType = (RecordType) restType;
                BMap<BString, Object> nextValue = updateNextValue(recordType, xmlItem.getQName().getLocalPart(),
                        restType, mapValue, analyzerData);
                currentNode = nextValue;
                analyzerData.attributeHierarchy.push(DataUtils.getAllAttributesInRecordType(recordType));
                analyzerData.fieldHierarchy.push(DataUtils.getAllFieldsInRecordType(recordType, analyzerData));
                analyzerData.restTypes.push(recordType.getRestFieldType());
                handleAttributes(xmlItem, nextValue, recordType, analyzerData);
                traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                DataUtils.validateRequiredFields(nextValue, analyzerData);
                analyzerData.fieldHierarchy.pop();
                analyzerData.attributeHierarchy.pop();
                analyzerData.restTypes.pop();
                currentNode = analyzerData.nodesStack.pop();
                return;
            } else if (restType.getTag() == TypeTags.ARRAY_TAG) {
                BString bElementName = fromString(elemName);
                if (!mapValue.containsKey(bElementName)) {
                    BArray array = DataUtils.createNewAnydataList();
                    mapValue.put(bElementName, array);
                }
                Type elementType = ((ArrayType) restType).getElementType();
                if (elementType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    RecordType recordType = (RecordType) elementType;
                    BMap<BString, Object> nextValue = updateNextValue(recordType, elemName, restType, mapValue,
                            analyzerData);
                    currentNode = nextValue;
                    analyzerData.attributeHierarchy.push(DataUtils.getAllAttributesInRecordType(recordType));
                    analyzerData.fieldHierarchy.push(DataUtils.getAllFieldsInRecordType(recordType, analyzerData));
                    analyzerData.restTypes.push(recordType.getRestFieldType());
                    handleAttributes(xmlItem, nextValue, recordType, analyzerData);
                    traverseXml(xmlItem.getChildrenSeq(), elementType, analyzerData);
                    DataUtils.validateRequiredFields(nextValue, analyzerData);
                    analyzerData.fieldHierarchy.pop();
                    analyzerData.attributeHierarchy.pop();
                    analyzerData.restTypes.pop();
                    currentNode = analyzerData.nodesStack.pop();
                    return;
                }
            }

            BString bElementName = fromString(elemName);
            if (mapValue.containsKey(bElementName)) {
                BArray arrayValue = DataUtils.createNewAnydataList();
                arrayValue.append(mapValue.get(bElementName));
                mapValue.put(bElementName, arrayValue);

                if (isNextElementContent(xmlItem)) {
                    traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                    return;
                }
                BMap<BString, Object> nextValue = ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA);
                arrayValue.append(nextValue);
                analyzerData.nodesStack.push(currentNode);
                currentNode = nextValue;
                traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                currentNode = analyzerData.nodesStack.pop();
                return;
            }

            if (isNextElementContent(xmlItem)) {
                traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
                return;
            }
            BMap<BString, Object> nextValue = ValueCreator.createMapValue(PredefinedTypes.TYPE_ANYDATA);
            mapValue.put(bElementName, nextValue);
            analyzerData.nodesStack.push(currentNode);
            currentNode = nextValue;
            traverseXml(xmlItem.getChildrenSeq(), restType, analyzerData);
            currentNode = analyzerData.nodesStack.pop();
        }

        private boolean isNextElementContent(BXmlItem xml) {
            List<BXml> newSequence = filterEmptyValuesOrCommentOrPi(xml.getChildrenSeq().getChildrenList());
            for (BXml bXml : newSequence) {
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
            for (BXml bXml: sequence) {
                if (isCommentOrPi(bXml)) {
                    continue;
                } else {
                    traverseXml(bXml, type, analyzerData);
                }
            }
            return currentNode;
        }

        private static boolean isCommentOrPi(BXml bxml) {
            return bxml.getNodeType() == XmlNodeType.COMMENT || bxml.getNodeType() == XmlNodeType.PI;
        }

        private BXml validateRootElement(BXml xml, RecordType recordType, XmlAnalyzerData analyzerData) {
            if (xml.getNodeType() == XmlNodeType.SEQUENCE) {
                List<BXml> newSequence = filterEmptyValuesOrCommentOrPi(((BXmlSequence) xml).getChildrenList());
                if (newSequence.size() == 1) {
                    return validateRootElement(newSequence.get(0), recordType, analyzerData);
                }
                throw DataUtils.getXmlError("XML root element is missing");
            } else if (xml.getNodeType() == XmlNodeType.TEXT) {
                throw DataUtils.getXmlError("XML root element is missing");
            }
            BXmlItem xmlItem = (BXmlItem) xml;
            analyzerData.rootRecord = recordType;
            QName qName = xmlItem.getQName();
            String elementName = qName.getLocalPart();
            analyzerData.rootElement =
                    DataUtils.validateAndGetXmlNameFromRecordAnnotation(recordType, recordType.getName(), elementName);
            DataUtils.validateTypeNamespace(qName.getPrefix(), qName.getNamespaceURI(), recordType);

            // Keep track of fields and attributes
            analyzerData.attributeHierarchy.push(DataUtils.getAllAttributesInRecordType(recordType));
            analyzerData.fieldHierarchy.push(DataUtils.getAllFieldsInRecordType(recordType, analyzerData));
            analyzerData.restTypes.push(recordType.getRestFieldType());
            handleAttributes(xmlItem, (BMap<BString, Object>) currentNode, recordType, analyzerData);
            return xmlItem.getChildrenSeq();
        }

        private void handleAttributes(BXmlItem xmlItem, BMap<BString, Object> currentNode, RecordType recordType,
                                      XmlAnalyzerData analyzerData) {
            HashSet<String> innerElements = findAllInnerElement(xmlItem);
            BMap<BString, BString> attributeMap = xmlItem.getAttributesMap();
            Map<String, String> nsPrefixMap = getNamespacePrefixes(attributeMap);
            for (Map.Entry<BString, BString> entry : attributeMap.entrySet()) {
                if (isNamespacePrefixEntry(entry)) {
                    continue;
                }
                BString key = entry.getKey();
                QName attribute = getAttributePreservingNamespace(nsPrefixMap, key.getValue());
                String attributeName = attribute.getLocalPart();
                Field field = analyzerData.attributeHierarchy.peek().remove(attributeName);
                if (field == null) {
                    if (innerElements.contains(attributeName)) {
                        // Element and Attribute have same name. Priority given to element.
                        return;
                    }
                    field = analyzerData.fieldHierarchy.peek().get(attributeName);
                }

                if (field == null) {
                    continue;
                }

                DataUtils.validateFieldNamespace(attribute.getPrefix(), attribute.getNamespaceURI(),
                        field.getFieldName(), recordType);

                try {
                    currentNode.put(StringUtils.fromString(field.getFieldName()),
                            DataUtils.convertStringToExpType(attributeMap.get(key), field.getFieldType()));
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

        private QName getAttributePreservingNamespace(Map<String, String> nsPrefixMap, String attributeKey) {
            int nsEndIndex = attributeKey.lastIndexOf('}');
            if (nsEndIndex > 0) {
                String ns = attributeKey.substring(1, nsEndIndex);
                String local = attributeKey.substring(nsEndIndex + 1);
                String nsPrefix = nsPrefixMap.get(ns);
                // `!nsPrefix.equals("xmlns")` because attributes does not belong to default namespace.
                if (nsPrefix == null) {
                    nsPrefix = "";
                }
                return new QName(ns, local, nsPrefix);
            }
            return new QName(attributeKey);
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
