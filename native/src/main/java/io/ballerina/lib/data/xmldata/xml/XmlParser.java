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

import io.ballerina.lib.data.xmldata.FromString;
import io.ballerina.lib.data.xmldata.utils.Constants;
import io.ballerina.lib.data.xmldata.utils.DataUtils;
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
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.DTD;
import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * Convert Xml string to a ballerina record.
 *
 * @since 0.1.0
 */
public class XmlParser {

    // XMLInputFactory2
    private static final XMLInputFactory xmlInputFactory;

    static {
        xmlInputFactory = XMLInputFactory.newInstance();
        xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    }

    private XMLStreamReader xmlStreamReader;
    public static final String PARSE_ERROR = "failed to parse xml";
    public static final String PARSE_ERROR_PREFIX = PARSE_ERROR + ": ";

    public XmlParser(Reader stringReader) {
        try {
            xmlStreamReader = xmlInputFactory.createXMLStreamReader(stringReader);
        } catch (XMLStreamException e) {
            handleXMLStreamException(e);
        }
    }

    public static Object parse(Reader reader, BMap<BString, Object> options, Type type) {
        try {
            XmlParserData xmlParserData = new XmlParserData();
            updateOptions(options, xmlParserData);
            XmlParser xmlParser = new XmlParser(reader);
            return xmlParser.parse(type, xmlParserData);
        } catch (BError e) {
            return e;
        } catch (Throwable e) {
            return DiagnosticLog.error(DiagnosticErrorCode.XML_PARSE_ERROR, e.getMessage());
        }
    }

    private static void updateOptions(BMap<BString, Object> options, XmlParserData xmlParserData) {
        xmlParserData.attributePrefix = options.get(Constants.ATTRIBUTE_PREFIX).toString();
        xmlParserData.textFieldName = options.get(Constants.TEXT_FIELD_NAME).toString();
        xmlParserData.allowDataProjection = (boolean) options.get(Constants.ALLOW_DATA_PROJECTION);
    }

    private void handleXMLStreamException(Exception e) {
        String reason = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        if (reason == null) {
            throw DiagnosticLog.getXmlError(PARSE_ERROR);
        }
        throw DiagnosticLog.getXmlError(PARSE_ERROR_PREFIX + reason);
    }

    public Object parse(Type type, XmlParserData xmlParserData) {
        if (type.getTag() != TypeTags.RECORD_TYPE_TAG) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, Constants.RECORD, type.getName());
        }
        xmlParserData.rootRecord = (RecordType) type;
        Object result = parse(xmlParserData);
        reset(xmlParserData);
        return result;
    }

    private void reset(XmlParserData xmlParserData) {
        xmlParserData.fieldHierarchy.clear();
        xmlParserData.visitedFieldHierarchy.clear();
        xmlParserData.attributeHierarchy.clear();
        xmlParserData.restTypes.clear();
        xmlParserData.nodesStack.clear();
        xmlParserData.parents.clear();
        xmlParserData.siblings.clear();
        xmlParserData.recordTypeStack.clear();
        xmlParserData.restFieldsPoints.clear();
        xmlParserData.arrayIndexes.clear();
    }

    public Object parse(XmlParserData xmlParserData) {
        try {
            parseRootElement(xmlStreamReader, xmlParserData);

            boolean readNext = false;
            int next;
            while (xmlStreamReader.hasNext()) {
                if (readNext) {
                    next = xmlStreamReader.getEventType();
                } else {
                    next = xmlStreamReader.next();
                }
                readNext = parseXmlElements(next, xmlParserData);
            }
        } catch (NumberFormatException e) {
            throw DiagnosticLog.getXmlError(PARSE_ERROR_PREFIX + e);
        } catch (BError e) {
            throw e;
        } catch (Exception e) {
            handleXMLStreamException(e);
        }

        return xmlParserData.currentNode;
    }

    private boolean parseXmlElements(int next, XmlParserData xmlParserData) throws XMLStreamException {
        switch (next) {
            case START_ELEMENT -> readElement(xmlStreamReader, xmlParserData);
            case END_ELEMENT -> endElement(xmlStreamReader, xmlParserData);
            case CDATA -> readText(xmlStreamReader, true, xmlParserData);
            case CHARACTERS -> {
                readText(xmlStreamReader, false, xmlParserData);
                return true;
            }
            case END_DOCUMENT -> buildDocument(xmlParserData);
            case PROCESSING_INSTRUCTION, COMMENT, DTD -> { } // Ignore
            default -> {
                assert false;
            }
        }
        return false;
    }

    public void parseRecordRest(String startElementName, XmlParserData xmlParserData) {
        try {
            boolean readNext = false;
            int next;
            while (xmlStreamReader.hasNext()) {
                if (readNext) {
                    next = xmlStreamReader.getEventType();
                } else {
                    next = xmlStreamReader.next();
                }

                // Terminate the record rest field parsing if the end element is reached.
                if (next == END_ELEMENT) {
                    QName endElement = xmlStreamReader.getName();
                    if (endElement.getLocalPart().equals(startElementName)) {
                        validateRequiredFields(xmlParserData);
                        popExpectedTypeStacks(xmlParserData);
                        break;
                    }
                }
                readNext = parseXmlElements(next, xmlParserData);
            }
        } catch (BError e) {
            throw e;
        } catch (Exception e) {
            handleXMLStreamException(e);
        }
    }

    private void parseRootElement(XMLStreamReader xmlStreamReader,
                                  XmlParserData xmlParserData) throws XMLStreamException {
        if (xmlStreamReader.hasNext()) {
            int next = xmlStreamReader.next();
            if (next == COMMENT || next == PROCESSING_INSTRUCTION) {
                parseRootElement(xmlStreamReader, xmlParserData);
                return;
            } else if (next != START_ELEMENT) {
                throw DiagnosticLog.error(DiagnosticErrorCode.XML_ROOT_MISSING);
            }
        }

        RecordType rootRecord = xmlParserData.rootRecord;
        xmlParserData.currentNode = ValueCreator.createRecordValue(rootRecord.getPackage(), rootRecord.getName());
        QualifiedName elementQName = getElementName(xmlStreamReader);
        xmlParserData.rootElement =
                DataUtils.validateAndGetXmlNameFromRecordAnnotation(rootRecord, rootRecord.getName(), elementQName);
        DataUtils.validateTypeNamespace(elementQName.getPrefix(), elementQName.getNamespaceURI(), rootRecord);

        // Keep track of fields and attributes
        updateExpectedTypeStacks(rootRecord, xmlParserData);
        handleAttributes(xmlStreamReader, xmlParserData);
        xmlParserData.arrayIndexes.push(new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private void readText(XMLStreamReader xmlStreamReader,
                          boolean isCData,
                          XmlParserData xmlParserData) throws XMLStreamException {
        Field currentField = xmlParserData.currentField;
        TextValue textValue = new TextValue();
        String text;
        if (isCData) {
            text = xmlStreamReader.getText();
        } else {
            handleTruncatedCharacters(xmlStreamReader, textValue);
            text = textValue.text;
        }

        if (text.strip().isBlank()) {
            return;
        }

        String textFieldName = xmlParserData.textFieldName;
        if (currentField == null) {
            QualifiedName contentQName = new QualifiedName("", textFieldName, "");
            if (!xmlParserData.fieldHierarchy.peek().contains(contentQName)) {
                if (xmlParserData.visitedFieldHierarchy.peek().contains(contentQName)) {
                    currentField = xmlParserData.visitedFieldHierarchy.peek().get(contentQName);
                } else if (!xmlParserData.allowDataProjection) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, textFieldName,
                            xmlParserData.rootRecord);
                } else {
                    return;
                }
            } else {
                currentField = xmlParserData.fieldHierarchy.peek().remove(contentQName);
            }
        }

        BString bText = StringUtils.fromString(text);
        String fieldName = currentField.getFieldName();
        BString bFieldName = StringUtils.fromString(fieldName);
        Type fieldType = TypeUtils.getReferredType(currentField.getFieldType());

        if (textValue.isCommentInTheMiddle && !DataUtils.isStringValueAssignable(fieldType.getTag())) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, fieldType, PredefinedTypes.TYPE_STRING);
        }

        Object temp = xmlParserData.currentNode.get(bFieldName);
        if (temp instanceof BArray && !DataUtils.isAnydataOrJson(fieldType.getTag())) {
            if (fieldName.equals(textFieldName)) {
                xmlParserData.currentNode.put(bFieldName, convertStringToRestExpType(bText, fieldType));
                return;
            }

            ArrayType arrayType = (ArrayType) fieldType;
            int currentIndex = xmlParserData.arrayIndexes.peek().get(fieldName);
            if (arrayType.getState() == ArrayType.ArrayState.CLOSED
                    && arrayType.getSize() <= currentIndex) {
                DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(xmlParserData.allowDataProjection);
                return;
            }

            ((BArray) xmlParserData.currentNode.get(bFieldName)).add(currentIndex,
                    convertStringToRestExpType(bText, fieldType));
            return;
        }

        switch (fieldType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> handleContentFieldInRecordType((RecordType) fieldType, bText,
                    xmlParserData);
            case TypeTags.ARRAY_TAG ->
                addTextToCurrentNodeIfExpTypeIsArray((ArrayType) fieldType, bFieldName, bText, xmlParserData);
            case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                convertTextAndUpdateCurrentNode(xmlParserData.currentNode,
                        (BMap<BString, Object>) xmlParserData.nodesStack.pop(),
                        bFieldName, bText, fieldType, xmlParserData);
            default -> xmlParserData.currentNode.put(bFieldName, convertStringToRestExpType(bText, fieldType));
        }
    }

    private void convertTextAndUpdateCurrentNode(BMap<BString, Object> currentNode,
                                                 BMap<BString, Object> parent,
                                                 BString currentFieldName,
                                                 BString bText, Type restType,
                                                 XmlParserData xmlParserData) {
        Object currentElement = currentNode.get(currentFieldName);
        Object result = convertStringToRestExpType(bText, restType);

        if (currentElement == null && !currentNode.isEmpty()) { // Add text to the #content field
            currentNode.put(StringUtils.fromString(Constants.CONTENT), result);
        } else if (parent.get(currentFieldName) instanceof BArray bArray) {
             bArray.add(bArray.getLength() - 1, result);
        } else {
            parent.put(currentFieldName, result);
        }

        xmlParserData.currentNode = parent;
        popExpectedTypeStacks(xmlParserData);
        updateSiblingAndRootRecord(xmlParserData);
    }

    @SuppressWarnings("unchecked")
    private void addTextToCurrentNodeIfExpTypeIsArray(ArrayType fieldType, BString bFieldName, BString bText,
                                                      XmlParserData xmlParserData) {
        int elementTypeTag = TypeUtils.getReferredType(fieldType.getElementType()).getTag();
        switch (elementTypeTag) {
            case TypeTags.RECORD_TYPE_TAG -> handleContentFieldInRecordType((RecordType) fieldType.getElementType(),
                    bText, xmlParserData);
            case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG -> {
                BArray tempArr = (BArray) ((BMap<BString, Object>) xmlParserData.nodesStack.peek()).get(bFieldName);
                // TODO: Using this -2 here seems strange for me. Find a better way to handle this.
                HashMap<String, Integer> indexes
                        = xmlParserData.arrayIndexes.get(xmlParserData.arrayIndexes.size() - 2);
                int currentIndex = indexes.get(bFieldName.getValue());
                if (fieldType.getState() == ArrayType.ArrayState.CLOSED && currentIndex >= fieldType.getSize()) {
                    DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(xmlParserData.allowDataProjection);
                    return;
                }
                tempArr.add(currentIndex, convertStringToRestExpType(bText, fieldType));
            }
        }
    }

    private void handleTruncatedCharacters(XMLStreamReader xmlStreamReader, TextValue textValue)
            throws XMLStreamException {
        StringBuilder textBuilder = new StringBuilder();
        while (xmlStreamReader.getEventType() == CHARACTERS) {
            textBuilder.append(xmlStreamReader.getText());
            if (xmlStreamReader.next() == COMMENT) {
                textValue.isCommentInTheMiddle = true;
                xmlStreamReader.next();
            }
        }
        textValue.text = textBuilder.toString();
    }

    @SuppressWarnings("unchecked")
    private void handleContentFieldInRecordType(RecordType recordType, BString text, XmlParserData xmlParserData) {
        popExpectedTypeStacks(xmlParserData);
        updateSiblingAndRootRecord(xmlParserData);
        for (String key : recordType.getFields().keySet()) {
            if (key.contains(xmlParserData.textFieldName)) {
                xmlParserData.currentNode.put(StringUtils.fromString(key),
                        convertStringToExpType(text, recordType.getFields().get(key).getFieldType()));
                xmlParserData.currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
                return;
            }
        }

        Type restType = TypeUtils.getReferredType(recordType.getRestFieldType());
        if (restType == null) {
            return;
        }

        xmlParserData.currentNode.put(StringUtils.fromString(xmlParserData.textFieldName),
                convertStringToRestExpType(text, restType));
        xmlParserData.currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
    }

    private Object convertStringToExpType(BString value, Type expType) {
        if (expType.getTag() == TypeTags.ARRAY_TAG) {
            expType = ((ArrayType) expType).getElementType();
        }
        Object result = FromString.fromStringWithType(value, expType);
        if (result instanceof BError) {
            throw (BError) result;
        }
        return result;
    }

    private Object convertStringToRestExpType(BString value, Type expType) {
        switch (expType.getTag()) {
            case TypeTags.ARRAY_TAG -> {
                return convertStringToRestExpType(value, ((ArrayType) expType).getElementType());
            }
            case TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.STRING_TAG,
                    TypeTags.BOOLEAN_TAG, TypeTags.UNION_TAG -> {
                return convertStringToExpType(value, expType);
            }
            case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG -> {
                return convertStringToExpType(value, PredefinedTypes.TYPE_JSON);
            }
            case TypeTags.TYPE_REFERENCED_TYPE_TAG -> {
                return convertStringToRestExpType(value, TypeUtils.getReferredType(expType));
            }
        }
        throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_REST_TYPE, expType.getName());
    }

    private void buildDocument(XmlParserData xmlParserData) {
        validateRequiredFields(xmlParserData);
    }

    @SuppressWarnings("unchecked")
    private void endElement(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        xmlParserData.currentField = null;
        QualifiedName elemQName = getElementName(xmlStreamReader);
        QualifiedNameMap<Boolean> siblings = xmlParserData.siblings;
        Stack<QualifiedNameMap<Boolean>> parents = xmlParserData.parents;
        if (siblings.contains(elemQName) && !siblings.get(elemQName)) {
            siblings.put(elemQName, true);
        }
        if (parents.isEmpty() || !parents.peek().contains(elemQName)) {
            return;
        }

        validateRequiredFields(xmlParserData);
        xmlParserData.currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
        popExpectedTypeStacks(xmlParserData);
        updateSiblingAndRootRecord(xmlParserData);
    }

    private void validateRequiredFields(XmlParserData xmlParserData) {
        Map<QualifiedName, Field> remainingFields = xmlParserData.fieldHierarchy.peek().getMembers();
        for (Field field : remainingFields.values()) {
            if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.REQUIRED)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_FIELD_NOT_PRESENT, field.getFieldName());
            }
        }

        Map<QualifiedName, Field> remainingAttributes = xmlParserData.attributeHierarchy.peek().getMembers();
        for (Field attribute : remainingAttributes.values()) {
            if (!SymbolFlags.isFlagOn(attribute.getFlags(), SymbolFlags.OPTIONAL)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_ATTRIBUTE_NOT_PRESENT, attribute.getFieldName());
            }
        }
    }

    private void readElement(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        QualifiedName elemQName = getElementName(xmlStreamReader);
        QualifiedNameMap<Field> fieldMap = xmlParserData.fieldHierarchy.peek();
        Field currentField = null;
        if (xmlParserData.visitedFieldHierarchy.peek().contains(elemQName)) {
            currentField = xmlParserData.visitedFieldHierarchy.peek().get(elemQName);
        } else if (fieldMap.contains(elemQName)) {
            elemQName = fieldMap.getMatchedQualifiedName(elemQName);
            currentField = fieldMap.remove(elemQName);
        }
        xmlParserData.currentField = currentField;
        if (currentField == null) {
            String elemName = elemQName.getLocalPart();
            if (xmlParserData.restTypes.peek() != null) {
                if (fieldMap.contains(elemName)) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, elemName,
                            xmlParserData.rootRecord);
                }
                xmlParserData.currentNode = handleRestField(xmlParserData);
            } else if (!xmlParserData.allowDataProjection) {
                throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, elemName,
                        xmlParserData.rootRecord);
            }
            return;
        }

        xmlParserData.visitedFieldHierarchy.peek().put(elemQName, currentField);
        BMap<BString, Object> currentNode = xmlParserData.currentNode;
        String fieldName = currentField.getFieldName();
        BString bFieldName = StringUtils.fromString(fieldName);
        Object temp = currentNode.get(bFieldName);
        Type fieldType = currentField.getFieldType();
        Type referredFieldType = TypeUtils.getReferredType(fieldType);
        if (!xmlParserData.siblings.contains(elemQName)) {
            xmlParserData.siblings.put(elemQName, false);
        } else {
            if (DataUtils.isAnydataOrJson(referredFieldType.getTag()) && !(temp instanceof BArray)) {
                BArray tempArray = DataUtils.createArrayValue(referredFieldType);
                tempArray.append(temp);
                currentNode.put(bFieldName, tempArray);
            } else if (referredFieldType.getTag() != TypeTags.ARRAY_TAG) {
                throw DiagnosticLog.error(DiagnosticErrorCode.FOUND_ARRAY_FOR_NON_ARRAY_TYPE, fieldType, fieldName);
            }
        }

        if (DataUtils.isRegExpType(fieldType)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE);
        }

        initializeNextValueBasedOnExpectedType(fieldName, fieldType, temp, currentNode, xmlParserData);
    }

    private void initializeNextValueBasedOnExpectedType(String fieldName, Type fieldType, Object temp,
                                                        BMap<BString, Object> currentNode,
                                                        XmlParserData xmlParserData) {
        switch (fieldType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG ->
                    updateNextRecord(xmlStreamReader, xmlParserData, fieldName, fieldType, (RecordType) fieldType);
            case TypeTags.ARRAY_TAG -> {
                if (temp == null) {
                    xmlParserData.arrayIndexes.peek().put(fieldName, 0);
                    currentNode.put(StringUtils.fromString(fieldName),
                            ValueCreator.createArrayValue((ArrayType) fieldType));
                } else {
                    HashMap<String, Integer> indexes = xmlParserData.arrayIndexes.peek();
                    indexes.put(fieldName, indexes.get(fieldName) + 1);
                }
                updateNextArrayMember(xmlStreamReader, xmlParserData, fieldName, fieldType,
                        ((ArrayType) fieldType).getElementType());
            }
            case TypeTags.MAP_TAG, TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                    initializeAttributesForNextMappingValue(xmlParserData, fieldName, fieldType);
            case TypeTags.TYPE_REFERENCED_TYPE_TAG ->
                initializeNextValueBasedOnExpectedType(fieldName, TypeUtils.getReferredType(fieldType), temp,
                        currentNode, xmlParserData);
        }
    }

    private void updateNextArrayMember(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData,
                                       String fieldName, Type fieldType, Type type) {
        if (DataUtils.isRegExpType(type)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE);
        }

        switch (type.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> updateNextRecord(xmlStreamReader, xmlParserData, fieldName,
                    fieldType, (RecordType) type);
            case TypeTags.MAP_TAG, TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                    initializeAttributesForNextMappingValue(xmlParserData, fieldName, fieldType);
            case TypeTags.TYPE_REFERENCED_TYPE_TAG ->
                updateNextArrayMember(xmlStreamReader, xmlParserData, fieldName, fieldType,
                        TypeUtils.getReferredType(type));
        }
    }

    private void initializeAttributesForNextMappingValue(XmlParserData xmlParserData, String fieldName,
                                                         Type fieldType) {
        xmlParserData.parents.push(xmlParserData.siblings);
        xmlParserData.siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
        BMap<BString, Object> nextMapValue = updateNextMappingValue(xmlParserData, fieldName, fieldType);
        handleAttributesRest(xmlStreamReader, fieldType, nextMapValue);
        xmlParserData.currentNode = nextMapValue;
    }

    private BMap<BString, Object> updateNextMappingValue(XmlParserData xmlParserData, String fieldName,
                                                         Type fieldType) {
        BMap<BString, Object> nextValue;
        Type type = fieldType;
        if (fieldType.getTag() == TypeTags.ARRAY_TAG) {
            type = ((ArrayType) fieldType).getElementType();
        }

        if (type.getTag() == TypeTags.MAP_TAG) {
            nextValue = ValueCreator.createMapValue((MapType) type);
            xmlParserData.restTypes.push(((MapType) type).getConstrainedType());
        } else {
            nextValue = ValueCreator.createMapValue(TypeCreator.createMapType(type));
            xmlParserData.restTypes.push(type);
        }

        xmlParserData.attributeHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
        xmlParserData.fieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
        xmlParserData.visitedFieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
        xmlParserData.recordTypeStack.push(null);
        BMap<BString, Object> currentNode = xmlParserData.currentNode;
        Object temp = currentNode.get(StringUtils.fromString(fieldName));
        if (temp instanceof BArray) {
            if (DataUtils.isAnydataOrJson(fieldType.getTag())) {
                ((BArray) temp).append(nextValue);
            } else {
                int arraySize = getArraySize(fieldType, temp);
                int currentIndex = xmlParserData.arrayIndexes.peek().get(fieldName);
                if (currentIndex < arraySize || arraySize == -1) {
                    ((BArray) temp).add(currentIndex, nextValue);
                } else {
                    DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(xmlParserData.allowDataProjection);
                }
            }
        } else {
            currentNode.put(StringUtils.fromString(fieldName), nextValue);
        }
        xmlParserData.nodesStack.push(currentNode);
        xmlParserData.arrayIndexes.push(new HashMap<>());
        return nextValue;
    }

    private int getArraySize(Type type, Object temp) {
        return type.getTag() == TypeTags.ARRAY_TAG ?
                ((ArrayType) type).getSize() : ((ArrayType) TypeUtils.getType(temp)).getSize();
    }

    private void updateNextRecord(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData, String fieldName,
                                  Type fieldType, RecordType recordType) {
        xmlParserData.parents.push(xmlParserData.siblings);
        xmlParserData.siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
        xmlParserData.recordTypeStack.push(xmlParserData.rootRecord);
        xmlParserData.rootRecord = recordType;
        xmlParserData.currentNode = updateNextValue(recordType, fieldName, fieldType, xmlParserData);
        QName qName = xmlStreamReader.getName();
        DataUtils.validateTypeNamespace(qName.getPrefix(), qName.getNamespaceURI(), recordType);
        handleAttributes(xmlStreamReader, xmlParserData);
    }

    private BMap<BString, Object> updateNextValue(RecordType rootRecord, String fieldName, Type fieldType,
                                                  XmlParserData xmlParserData) {
        BMap<BString, Object> nextValue = ValueCreator.createRecordValue(rootRecord.getPackage(), rootRecord.getName());
        updateExpectedTypeStacks(rootRecord, xmlParserData);
        BMap<BString, Object> currentNode = xmlParserData.currentNode;
        Object temp = currentNode.get(StringUtils.fromString(fieldName));
        if (temp instanceof BArray) {
            ArrayType arrayType = (ArrayType) fieldType;
            int currentIndex = xmlParserData.arrayIndexes.peek().get(fieldName);
            if (arrayType.getState() == ArrayType.ArrayState.OPEN || currentIndex < arrayType.getSize()) {
                ((BArray) temp).add(currentIndex, nextValue);
            } else {
                DataUtils.logArrayMismatchErrorIfProjectionNotAllowed(xmlParserData.allowDataProjection);
            }
        } else {
            currentNode.put(StringUtils.fromString(fieldName), nextValue);
        }
        xmlParserData.nodesStack.push(currentNode);
        xmlParserData.arrayIndexes.push(new HashMap<>());
        return nextValue;
    }

    private void updateExpectedTypeStacks(RecordType recordType, XmlParserData xmlParserData) {
        xmlParserData.attributeHierarchy.push(new QualifiedNameMap<>(getAllAttributesInRecordType(recordType)));
        xmlParserData.fieldHierarchy.push(new QualifiedNameMap<>(getAllFieldsInRecordType(recordType, xmlParserData)));
        xmlParserData.visitedFieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
        xmlParserData.restTypes.push(recordType.getRestFieldType());
    }

    private void popExpectedTypeStacks(XmlParserData xmlParserData) {
        popMappingTypeStacks(xmlParserData);
        xmlParserData.attributeHierarchy.pop();
        xmlParserData.arrayIndexes.pop();
    }

    private void popMappingTypeStacks(XmlParserData xmlParserData) {
        xmlParserData.fieldHierarchy.pop();
        xmlParserData.visitedFieldHierarchy.pop();
        xmlParserData.restTypes.pop();
    }

    private void updateSiblingAndRootRecord(XmlParserData xmlParserData) {
        xmlParserData.siblings = xmlParserData.parents.pop();
        xmlParserData.rootRecord = xmlParserData.recordTypeStack.pop();
    }

    @SuppressWarnings("unchecked")
    private BMap<BString, Object> handleRestField(XmlParserData xmlParserData) {
        QualifiedName restStartPoint = xmlParserData.parents.isEmpty() ?
                xmlParserData.rootElement : getLastElementInSiblings(xmlParserData.parents.peek().getMembers());
        xmlParserData.restFieldsPoints.push(restStartPoint);
        xmlParserData.nodesStack.push(xmlParserData.currentNode);
        return (BMap<BString, Object>) parseRestField(xmlParserData);
    }

    private Object parseRestField(XmlParserData xmlParserData) {
        int next = xmlStreamReader.getEventType();
        Optional<Object> restNode = handleRecordRestType(xmlParserData, xmlStreamReader);
        if (restNode.isPresent()) {
            return restNode.get();
        }

        BString currentFieldName = null;
        try {
            boolean readNext = false;
            while (!xmlParserData.restFieldsPoints.isEmpty()) {
                if (xmlParserData.restFieldsPoints.peek() == Constants.EXIT_REST_POINT) {
                    xmlParserData.restFieldsPoints.pop();
                    break;
                }

                switch (next) {
                    case START_ELEMENT -> currentFieldName = readElementRest(xmlStreamReader, xmlParserData);
                    case END_ELEMENT -> endElementRest(xmlStreamReader, xmlParserData);
                    case CDATA -> readTextRest(xmlStreamReader, currentFieldName, true, xmlParserData);
                    case CHARACTERS -> {
                        readTextRest(xmlStreamReader, currentFieldName, false, xmlParserData);
                        readNext = true;
                    }
                }

                if (xmlStreamReader.hasNext() && !xmlParserData.restFieldsPoints.isEmpty()) {
                    if (readNext) {
                        readNext = false;
                        next = xmlStreamReader.getEventType();
                    } else {
                        next = xmlStreamReader.next();
                    }
                } else {
                    break;
                }
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }

        return xmlParserData.nodesStack.pop();
    }

    private void updateExpectedTypeStacksOfRestType(Type restType, XmlParserData xmlParserData) {
        if (restType.getTag() == TypeTags.ARRAY_TAG) {
            updateExpectedTypeStacksOfRestType(((ArrayType) restType).getElementType(), xmlParserData);
        } else if (restType.getTag() == TypeTags.ANYDATA_TAG || restType.getTag() == TypeTags.JSON_TAG) {
            xmlParserData.fieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            xmlParserData.visitedFieldHierarchy.push(new QualifiedNameMap<>(new HashMap<>()));
            xmlParserData.restTypes.push(restType);
            xmlParserData.arrayIndexes.push(new HashMap<>());
        }
    }

    @SuppressWarnings("unchecked")
    private BString readElementRest(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        QualifiedName elemQName = getElementName(xmlStreamReader);
        BString currentFieldName = StringUtils.fromString(elemQName.getLocalPart());
        QualifiedName lastElement = getLastElementInSiblings(xmlParserData.siblings.getMembers());
        Type restType = TypeUtils.getReferredType(xmlParserData.restTypes.peek());

        if (!xmlParserData.siblings.isEmpty() && lastElement != null
                && !xmlParserData.siblings.getOrDefault(lastElement, true)) {
            xmlParserData.parents.push(xmlParserData.siblings);
            xmlParserData.siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
            updateExpectedTypeStacksOfRestType(restType, xmlParserData);
            xmlParserData.siblings.put(elemQName, false);
            BMap<BString, Object> next =
                    ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
            handleAttributesRest(xmlStreamReader, restType, next);

            Object temp = xmlParserData.currentNode.get(
                            StringUtils.fromString(lastElement.getLocalPart()));
            BMap<BString, Object> mapValue;
            if (temp instanceof BArray) {
                mapValue = ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
                mapValue.put(currentFieldName, next);
                ((BArray) temp).append(mapValue);
            } else {
                mapValue = (BMap<BString, Object>) temp;
                mapValue.put(currentFieldName, next);
            }
            xmlParserData.nodesStack.add(xmlParserData.currentNode);
            xmlParserData.currentNode = mapValue;
            return currentFieldName;
        }

        if (!xmlParserData.siblings.contains(elemQName)) {
            xmlParserData.siblings.put(elemQName, false);
            if (restType.getTag() == TypeTags.ARRAY_TAG) {
                BArray tempArray = DataUtils.createArrayValue(restType);
                xmlParserData.currentNode.put(currentFieldName, tempArray);
            } else {
                BMap<BString, Object> next =
                        ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
                xmlParserData.currentNode.put(currentFieldName, next);
                handleAttributesRest(xmlStreamReader, restType, next);
            }
            return currentFieldName;
        }

        Object currentElement = xmlParserData.currentNode.get(currentFieldName);

        if (currentElement instanceof BArray) {
            int elemTypeTag = ((BArray) currentElement).getElementType().getTag();
            if (elemTypeTag == TypeTags.ANYDATA_TAG || elemTypeTag == TypeTags.JSON_TAG) {
                xmlParserData.nodesStack.add(xmlParserData.currentNode);
                xmlParserData.parents.push(xmlParserData.siblings);
                xmlParserData.siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
                updateExpectedTypeStacksOfRestType(restType, xmlParserData);
                xmlParserData.currentNode = updateNextArrayMemberForRestType((BArray) currentElement, restType);
            }
            return currentFieldName;
        }

        if (!DataUtils.isArrayValueAssignable(restType.getTag())) {
            throw DiagnosticLog.error(
                    DiagnosticErrorCode.FOUND_ARRAY_FOR_NON_ARRAY_TYPE, restType, elemQName.getLocalPart());
        }
        BArray tempArray = DataUtils.createArrayValue(restType);
        tempArray.append(currentElement);
        xmlParserData.currentNode.put(currentFieldName, tempArray);

        int elemTypeTag = tempArray.getElementType().getTag();
        if (elemTypeTag == TypeTags.ANYDATA_TAG || elemTypeTag == TypeTags.JSON_TAG) {
            xmlParserData.nodesStack.add(xmlParserData.currentNode);
            xmlParserData.parents.push(xmlParserData.siblings);
            xmlParserData.siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
            updateExpectedTypeStacksOfRestType(restType, xmlParserData);
            xmlParserData.currentNode = updateNextArrayMemberForRestType(tempArray, restType);
        }
        return currentFieldName;
    }

    private BMap<BString, Object> updateNextArrayMemberForRestType(BArray tempArray, Type restType) {
        BMap<BString, Object> temp = ValueCreator.createMapValue(DataUtils.getMapTypeFromConstraintType(restType));
        tempArray.append(temp);
        handleAttributesRest(xmlStreamReader, restType, temp);
        return temp;
    }

    @SuppressWarnings("unchecked")
    private void endElementRest(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        QualifiedName elemQName = getElementName(xmlStreamReader);
        if (xmlParserData.siblings.contains(elemQName) && !xmlParserData.siblings.get(elemQName)) {
            xmlParserData.siblings.put(elemQName, true);
        }

        if (xmlParserData.parents.isEmpty() || !xmlParserData.parents.peek().contains(elemQName)) {
            return;
        }

        xmlParserData.currentNode = (BMap<BString, Object>) xmlParserData.nodesStack.pop();
        xmlParserData.siblings = xmlParserData.parents.pop();
        if (xmlParserData.siblings.contains(elemQName)) {
            // TODO: This place behaviour is strange need to check and fix it, Properly.
            popMappingTypeStacks(xmlParserData);
//            xmlParserData.attributeHierarchy.pop();
            xmlParserData.arrayIndexes.pop();
        }
        removeQNameFromRestFieldsPoints(elemQName, xmlParserData);
        xmlParserData.siblings.put(elemQName, true);
    }

    private void removeQNameFromRestFieldsPoints(QualifiedName elemQName, XmlParserData xmlParserData) {
        Stack<QualifiedName> restFieldsPoints = xmlParserData.restFieldsPoints;
        if (restFieldsPoints.contains(elemQName)) {
            restFieldsPoints.remove(elemQName);
        } else {
            restFieldsPoints.stream().filter(
                    qName -> qName.getLocalPart().equals(elemQName.getLocalPart())
                            && qName.getNamespaceURI().equals(Constants.NS_ANNOT_NOT_DEFINED)).findFirst().ifPresent(
                    restFieldsPoints::remove);
        }
    }

    @SuppressWarnings("unchecked")
    private void readTextRest(XMLStreamReader xmlStreamReader,
                              BString currentFieldName,
                              boolean isCData,
                              XmlParserData xmlParserData) throws XMLStreamException {
        TextValue textValue = new TextValue();
        String text;
        if (isCData) {
            text = xmlStreamReader.getText();
        } else {
            handleTruncatedCharacters(xmlStreamReader, textValue);
            text = textValue.text;
        }

        if (text.strip().isBlank()) {
            return;
        }

        BString bText = StringUtils.fromString(text);
        Type restType = TypeUtils.getReferredType(xmlParserData.restTypes.peek());

        if (textValue.isCommentInTheMiddle && !DataUtils.isStringValueAssignable(restType.getTag())) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, restType, PredefinedTypes.TYPE_STRING);
        }

        convertTextRestAndUpdateCurrentNodeForRestType(xmlParserData.currentNode,
                (BMap<BString, Object>) xmlParserData.nodesStack.peek(), currentFieldName, bText, restType,
                StringUtils.fromString(xmlParserData.textFieldName));
    }

    @SuppressWarnings("unchecked")
    private void convertTextRestAndUpdateCurrentNodeForRestType(BMap<BString, Object> currentNode,
                                                            BMap<BString, Object> parent,
                                                            BString currentFieldName,
                                                            BString bText, Type restType, BString textFieldName) {
        Object currentElement = currentNode.get(currentFieldName);
        Object result = convertStringToRestExpType(bText, restType);

        if (currentElement == null && DataUtils.isAnydataOrJson(restType.getTag()) &&
                parent != null && parent.get(currentFieldName) instanceof BArray bArray) {
            bArray.add(bArray.getLength() - 1, result);
            return;
        }

        if (currentElement instanceof BArray) {
            ((BArray) currentElement).append(result);
        } else if (currentElement instanceof BMap && !((BMap<BString, Object>) currentElement).isEmpty()) {
            ((BMap<BString, Object>) currentElement).put(textFieldName, result);
        } else {
            currentNode.put(currentFieldName, result);
        }
    }

    private QualifiedName getLastElementInSiblings(Map<QualifiedName, Boolean> siblings) {
        Object[] arr = siblings.keySet().toArray();
        QualifiedName lastElement = null;
        if (arr.length != 0) {
            lastElement = (QualifiedName) arr[arr.length - 1];
        }
        return lastElement;
    }

    @SuppressWarnings("unchecked")
    private Map<QualifiedName, Field> getAllFieldsInRecordType(RecordType recordType, XmlParserData xmlParserData) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        HashMap<String, QualifiedName> modifiedNames = new LinkedHashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (keyStr.contains(Constants.FIELD)) {
                // Capture namespace and name from the field annotation.
                String fieldName = keyStr.split(Constants.FIELD_REGEX)[1];
                Map<BString, Object> fieldAnnotation = (Map<BString, Object>) annotations.get(annotationKey);
                QualifiedName fieldQName = DataUtils.getFieldNameFromRecord(fieldAnnotation, fieldName);
                fieldQName.setLocalPart(getModifiedName(fieldAnnotation, fieldName));
                modifiedNames.put(fieldName, fieldQName);
            }
        }

        Map<QualifiedName, Field> fieldMap = new HashMap<>();
        Map<String, List<QualifiedName>> fieldNames = new HashMap<>();
        Map<String, Field> recordFields = recordType.getFields();
        for (String key : recordFields.keySet()) {
            QualifiedName modifiedQName =
                    modifiedNames.getOrDefault(key, new QualifiedName(Constants.NS_ANNOT_NOT_DEFINED, key, ""));
            String localName = modifiedQName.getLocalPart();
            if (fieldMap.containsKey(modifiedQName)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.DUPLICATE_FIELD, localName);
            } else if (fieldNames.containsKey(localName)) {
                if (modifiedQName.getNamespaceURI().equals(Constants.NS_ANNOT_NOT_DEFINED)) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.DUPLICATE_FIELD, localName);
                }
                List<QualifiedName> qNames = fieldNames.get(localName);
                qNames.forEach(qName -> {
                    if (qName.getNamespaceURI().equals(Constants.NS_ANNOT_NOT_DEFINED)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.DUPLICATE_FIELD, localName);
                    }
                });
                fieldMap.put(modifiedQName, recordFields.get(key));
                fieldNames.get(localName).add(modifiedQName);
            } else if (!xmlParserData.attributeHierarchy.peek().contains(modifiedQName)) {
                fieldMap.put(modifiedQName, recordFields.get(key));
                fieldNames.put(localName, new ArrayList<>(List.of(modifiedQName)));
            }
        }
        return fieldMap;
    }

    @SuppressWarnings("unchecked")
    private Map<QualifiedName, Field> getAllAttributesInRecordType(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        Map<QualifiedName, Field> attributes = new HashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (keyStr.contains(Constants.FIELD) && DataUtils.isAttributeField(annotationKey, annotations)) {
                String attributeName = keyStr.split(Constants.FIELD_REGEX)[1].replaceAll("\\\\", "");
                Map<BString, Object> fieldAnnotation = (Map<BString, Object>) annotations.get(annotationKey);
                QualifiedName fieldQName = DataUtils.getFieldNameFromRecord(fieldAnnotation, attributeName);
                fieldQName.setLocalPart(getModifiedName(fieldAnnotation, attributeName));
                attributes.put(fieldQName, recordType.getFields().get(attributeName));
            }
        }
        return attributes;
    }

    @SuppressWarnings("unchecked")
    private String getModifiedName(Map<BString, Object> fieldAnnotation, String attributeName) {
        for (BString key : fieldAnnotation.keySet()) {
            if (key.getValue().endsWith(Constants.NAME)) {
                return ((Map<BString, Object>) fieldAnnotation.get(key)).get(Constants.VALUE).toString();
            }
        }
        return attributeName;
    }

    private void handleAttributes(XMLStreamReader xmlStreamReader, XmlParserData xmlParserData) {
        for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
            QName attributeQName = xmlStreamReader.getAttributeName(i);
            QualifiedName attQName = new QualifiedName(attributeQName.getNamespaceURI(),
                    xmlParserData.attributePrefix + attributeQName.getLocalPart(), attributeQName.getPrefix());
            Field field = xmlParserData.attributeHierarchy.peek().remove(attQName);
            if (field == null) {
                Optional<Field> f = getFieldFromFieldHierarchy(attQName, xmlParserData);
                if (f.isEmpty()) {
                    continue;
                }
                field = f.get();
            }

            try {
                xmlParserData.currentNode.put(StringUtils.fromString(field.getFieldName()), convertStringToExpType(
                        StringUtils.fromString(xmlStreamReader.getAttributeValue(i)), field.getFieldType()));
            } catch (Exception e) {
                // Ignore: Expected type will mismatch when element and attribute having same name.
            }
        }
    }

    private Optional<Field> getFieldFromFieldHierarchy(QualifiedName attQName, XmlParserData xmlParserData) {
        Field field;
        if (xmlParserData.visitedFieldHierarchy.peek().contains(attQName)) {
            field = xmlParserData.visitedFieldHierarchy.peek().get(attQName);
        } else {
            field = xmlParserData.fieldHierarchy.peek().remove(attQName);
            xmlParserData.visitedFieldHierarchy.peek().put(attQName, field);
        }

        if (field != null) {
            return Optional.of(field);
        }

        if (xmlParserData.allowDataProjection) {
            return Optional.empty();
        }
        throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, attQName.getLocalPart(),
                xmlParserData.rootRecord);
    }

    private void handleAttributesRest(XMLStreamReader xmlStreamReader, Type restType, BMap<BString, Object> mapNode) {
        for (int i = 0; i < xmlStreamReader.getAttributeCount(); i++) {
            QName attributeQName = xmlStreamReader.getAttributeName(i);
            QualifiedName attQName = new QualifiedName(attributeQName.getNamespaceURI(),
                    attributeQName.getLocalPart(), attributeQName.getPrefix());

            try {
                mapNode.put(StringUtils.fromString(attQName.getLocalPart()), convertStringToRestExpType(
                        StringUtils.fromString(xmlStreamReader.getAttributeValue(i)), restType));
            } catch (Exception e) {
                // Ignore: Expected type will mismatch when element and attribute having same name.
            }
        }
    }

    private Optional<Object> handleRecordRestType(XmlParserData xmlParserData, XMLStreamReader xmlStreamReader) {
        xmlParserData.currentField = null;
        Type restType = TypeUtils.getReferredType(xmlParserData.restTypes.peek());
        QualifiedName elementQName = getElementName(xmlStreamReader);
        String fieldName = elementQName.getLocalPart(); // Since local part equals to field name.
        switch (restType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> {
                RecordType recordType = (RecordType) restType;
                updateStacksWhenRecordAsRestType(elementQName, xmlParserData);
                xmlParserData.currentNode = updateNextValue(recordType, fieldName, restType, xmlParserData);
                handleAttributes(xmlStreamReader, xmlParserData);
                parseRecordRest(fieldName, xmlParserData);
                xmlParserData.siblings.clear();
                return Optional.ofNullable(xmlParserData.nodesStack.pop());
            }
            case TypeTags.ARRAY_TAG -> {
                ArrayType arrayType = (ArrayType) restType;
                Type elemType = TypeUtils.getReferredType(arrayType.getElementType());
                HashMap<String, Integer> indexes = xmlParserData.arrayIndexes.peek();
                if (indexes.containsKey(fieldName)) {
                    indexes.put(fieldName, indexes.get(fieldName) + 1);
                } else {
                    indexes.put(fieldName, 0);
                }

                if (elemType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    updateStacksWhenRecordAsRestType(elementQName, xmlParserData);
                    // Create an array value since expected type is an array.
                    if (!xmlParserData.currentNode.containsKey(StringUtils.fromString(fieldName))) {
                        xmlParserData.currentNode.put(StringUtils.fromString(fieldName),
                                DataUtils.createArrayValue(arrayType));
                    }
                    xmlParserData.currentNode =
                            updateNextValue((RecordType) elemType, fieldName, arrayType, xmlParserData);
                    handleAttributes(xmlStreamReader, xmlParserData);
                    parseRecordRest(fieldName, xmlParserData);
                    xmlParserData.siblings.clear();
                    return Optional.ofNullable(xmlParserData.nodesStack.pop());
                }
            }
        }
        return Optional.empty();
    }

    private void updateStacksWhenRecordAsRestType(QualifiedName elementQName, XmlParserData xmlParserData) {
        if (!xmlParserData.siblings.contains(elementQName)) {
            xmlParserData.siblings.put(elementQName, false);
        }
        xmlParserData.restFieldsPoints.push(Constants.EXIT_REST_POINT);
        xmlParserData.parents.push(xmlParserData.siblings);
        xmlParserData.siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
    }

    private QualifiedName getElementName(XMLStreamReader xmlStreamReader) {
        QName qName = xmlStreamReader.getName();
        return new QualifiedName(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix());
    }

    /**
     * Represents the content of an XML element.
     *
     * @since 0.1.0
     */
    static class TextValue {
        String text;
        boolean isCommentInTheMiddle = false;
    }

    /**
     * Holds data required for the parsing.
     *
     * @since 0.1.0
     */
    public static class XmlParserData {
        private final Stack<Object> nodesStack = new Stack<>();
        private final Stack<QualifiedNameMap<Field>> fieldHierarchy = new Stack<>();
        Stack<QualifiedNameMap<Field>> visitedFieldHierarchy = new Stack<>();
        private final Stack<QualifiedNameMap<Field>> attributeHierarchy = new Stack<>();
        private final Stack<Type> restTypes = new Stack<>();
        private final Stack<QualifiedName> restFieldsPoints = new Stack<>();
        private final Stack<RecordType> recordTypeStack = new Stack<>();
        Stack<HashMap<String, Integer>> arrayIndexes = new Stack<>();
        private RecordType rootRecord;
        private Field currentField;
        private QualifiedName rootElement;
        private final Stack<QualifiedNameMap<Boolean>> parents = new Stack<>();
        private QualifiedNameMap<Boolean> siblings = new QualifiedNameMap<>(new LinkedHashMap<>());
        private BMap<BString, Object> currentNode;
        private String attributePrefix;
        private String textFieldName;
        private boolean allowDataProjection;
    }
}
