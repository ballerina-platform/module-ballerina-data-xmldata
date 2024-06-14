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

package io.ballerina.lib.data.xmldata.utils;

import io.ballerina.lib.data.xmldata.FromString;
import io.ballerina.lib.data.xmldata.xml.QualifiedName;
import io.ballerina.lib.data.xmldata.xml.QualifiedNameMap;
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
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;
import io.ballerina.stdlib.constraint.Constraints;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Stack;

import javax.xml.namespace.QName;

/**
 * A util class for the Data package's native implementation.
 *
 * @since 0.1.0
 */
public class DataUtils {
    private static final String ATTRIBUTE_PREFIX = "attribute_";
    private static final String VALUE = "value";
    private static String contentFieldName = Constants.CONTENT;

    @SuppressWarnings("unchecked")
    public static QualifiedName validateAndGetXmlNameFromRecordAnnotation(RecordType recordType, String recordName,
                                                                          QualifiedName elementName) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        String localName = recordName;
        for (BString annotationsKey : annotations.getKeys()) {
            String key = annotationsKey.getValue();
            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAME)) {
                String name = ((BMap<BString, Object>) annotations.get(annotationsKey)).get(Constants.VALUE).toString();
                String localPart = elementName.getLocalPart();
                if (!name.equals(localPart)) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.TYPE_NAME_MISMATCH_WITH_XML_ELEMENT, name, localPart);
                }
                localName = name;
                break;
            }
        }

        // Handle the namespace annotation.
        for (BString annotationsKey : annotations.getKeys()) {
            String key = annotationsKey.getValue();
            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAMESPACE)) {
                Map<BString, Object> namespaceAnnotation =
                        ((Map<BString, Object>) annotations.get(StringUtils.fromString(key)));
                BString uri = (BString) namespaceAnnotation.get(Constants.URI);
                BString prefix = (BString) namespaceAnnotation.get(Constants.PREFIX);
                return new QualifiedName(uri == null ? "" : uri.getValue(), localName,
                        prefix == null ? "" : prefix.getValue());
            }
        }
        return new QualifiedName(Constants.NS_ANNOT_NOT_DEFINED, localName, "");
    }

    public static void validateTypeNamespace(String prefix, String uri, RecordType recordType) {
        ArrayList<String> namespace = getNamespace(recordType);
        if (namespace.isEmpty() || prefix.equals(namespace.get(0)) && uri.equals(namespace.get(1))) {
            return;
        }
        throw DiagnosticLog.error(DiagnosticErrorCode.NAMESPACE_MISMATCH, recordType.getName());
    }

    @SuppressWarnings("unchecked")
    private static ArrayList<String> getNamespace(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        ArrayList<String> namespace = new ArrayList<>();
        for (BString annotationsKey : annotations.getKeys()) {
            String key = annotationsKey.getValue();
            if (!key.contains(Constants.FIELD) && key.endsWith(Constants.NAMESPACE)) {
                BMap<BString, Object> namespaceAnnotation = (BMap<BString, Object>) annotations.get(annotationsKey);
                namespace.add(namespaceAnnotation.containsKey(Constants.PREFIX) ?
                        ((BString) namespaceAnnotation.get(Constants.PREFIX)).getValue() : "");
                namespace.add(((BString) namespaceAnnotation.get(Constants.URI)).getValue());
                break;
            }
        }
        return namespace;
    }

    @SuppressWarnings("unchecked")
    public static Map<QualifiedName, Field> getAllFieldsInRecordType(RecordType recordType,
                                                                     XmlAnalyzerData analyzerData) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        HashMap<String, QualifiedName> modifiedNames = new LinkedHashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (keyStr.contains(Constants.FIELD)) {
                // Capture namespace and name from the field annotation.
                String fieldName = keyStr.split(Constants.FIELD_REGEX)[1].replaceAll("\\\\", "");
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
            } else if (!analyzerData.attributeHierarchy.peek().contains(modifiedQName)) {
                fieldMap.put(modifiedQName, recordFields.get(key));
                fieldNames.put(localName, new ArrayList<>(List.of(modifiedQName)));
            }
        }
        return fieldMap;
    }

    @SuppressWarnings("unchecked")
    public static Map<QualifiedName, Field> getAllAttributesInRecordType(RecordType recordType) {
        BMap<BString, Object> annotations = recordType.getAnnotations();
        Map<QualifiedName, Field> attributes = new HashMap<>();
        for (BString annotationKey : annotations.getKeys()) {
            String keyStr = annotationKey.getValue();
            if (keyStr.contains(Constants.FIELD) && DataUtils.isAttributeField(annotationKey, annotations)) {
                String attributeName = keyStr.split(Constants.FIELD_REGEX)[1].replaceAll("\\\\", "");
                Map<BString, Object> fieldAnnotation = (Map<BString, Object>) annotations.get(annotationKey);
                QualifiedName fieldQName = getFieldNameFromRecord(fieldAnnotation, attributeName);
                fieldQName.setLocalPart(getModifiedName(fieldAnnotation, attributeName));
                attributes.put(fieldQName, recordType.getFields().get(attributeName));
            }
        }
        return attributes;
    }

    @SuppressWarnings("unchecked")
    public static QualifiedName getFieldNameFromRecord(Map<BString, Object> fieldAnnotation, String fieldName) {
        for (BString key : fieldAnnotation.keySet()) {
            if (key.getValue().endsWith(Constants.NAMESPACE)) {
                Map<BString, Object> namespaceAnnotation = ((Map<BString, Object>) fieldAnnotation.get(key));
                BString uri = (BString) namespaceAnnotation.get(Constants.URI);
                BString prefix = (BString) namespaceAnnotation.get(Constants.PREFIX);
                return new QualifiedName(uri == null ? "" : uri.getValue(), fieldName,
                        prefix == null ? "" : prefix.getValue());
            }
        }
        return new QualifiedName(Constants.NS_ANNOT_NOT_DEFINED, fieldName, "");
    }

    @SuppressWarnings("unchecked")
    private static String getModifiedName(Map<BString, Object> fieldAnnotation, String attributeName) {
        for (BString key : fieldAnnotation.keySet()) {
            if (key.getValue().endsWith(Constants.NAME)) {
                return ((Map<BString, Object>) fieldAnnotation.get(key)).get(Constants.VALUE).toString();
            }
        }
        return attributeName;
    }

    public static BArray createNewAnydataList(Type type) {
        return ValueCreator.createArrayValue(getArrayTypeFromElementType(type));
    }

    public static QualifiedName getElementName(QName qName) {
        return new QualifiedName(qName.getNamespaceURI(), qName.getLocalPart(), qName.getPrefix());
    }

    public static Object convertStringToExpType(BString value, Type expType) {
        Object result;
        Type refferedType = TypeUtils.getReferredType(expType);
        switch (refferedType.getTag()) {
            case TypeTags.ANYDATA_TAG, TypeTags.ANY_TAG, TypeTags.JSON_TAG ->
                    result = FromString.fromStringWithType(value, PredefinedTypes.TYPE_JSON);
            case TypeTags.ARRAY_TAG -> result = convertStringToExpType(value,
                    ((ArrayType) refferedType).getElementType());
            default -> result = FromString.fromStringWithType(value, expType);
        }

        if (result instanceof BError) {
            throw (BError) result;
        }
        return result;
    }

    public static void validateRequiredFields(BMap<BString, Object> currentMapValue, XmlAnalyzerData analyzerData) {
        Map<QualifiedName, Field> fields = analyzerData.fieldHierarchy.peek().getMembers();
        for (QualifiedName key : fields.keySet()) {
            // Validate required array size
            Field field = fields.get(key);
            String fieldName = field.getFieldName();
            if (field.getFieldType().getTag() == TypeTags.ARRAY_TAG) {
                ArrayType arrayType = (ArrayType) field.getFieldType();
                if (arrayType.getSize() != -1
                        && arrayType.getSize() != ((BArray) currentMapValue.get(
                        StringUtils.fromString(fieldName))).getLength()) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
                }
            }

            if (!SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.OPTIONAL)
                    && !currentMapValue.containsKey(StringUtils.fromString(fieldName))) {
                throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_FIELD_NOT_PRESENT, fieldName);
            }
        }

        Map<QualifiedName, Field> attributes = analyzerData.attributeHierarchy.peek().getMembers();
        for (QualifiedName key : attributes.keySet()) {
            Field field = attributes.get(key);
            String fieldName = field.getFieldName();
            if (!SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.OPTIONAL)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_ATTRIBUTE_NOT_PRESENT, fieldName);
            }
        }
    }

    public static boolean isArrayValueAssignable(int typeTag) {
        return typeTag == TypeTags.ARRAY_TAG || typeTag == TypeTags.ANYDATA_TAG || typeTag == TypeTags.JSON_TAG;
    }

    public static boolean isStringValueAssignable(int typeTag) {
        return typeTag == TypeTags.STRING_TAG || typeTag == TypeTags.ANYDATA_TAG || typeTag == TypeTags.JSON_TAG;
    }

    public static ArrayType getValidArrayType(Type type) {
        return switch (type.getTag()) {
            case TypeTags.ARRAY_TAG -> (ArrayType) type;
            case TypeTags.ANYDATA_TAG -> PredefinedTypes.TYPE_ANYDATA_ARRAY;
            case TypeTags.JSON_TAG -> PredefinedTypes.TYPE_JSON_ARRAY;
            default -> null;
        };
    }

    public static ArrayType getArrayTypeFromElementType(Type type) {
        return switch (type.getTag()) {
            case TypeTags.ARRAY_TAG -> TypeCreator.createArrayType(((ArrayType) type).getElementType());
            case TypeTags.JSON_TAG -> PredefinedTypes.TYPE_JSON_ARRAY;
            case TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.STRING_TAG, TypeTags.BOOLEAN_TAG, TypeTags.BYTE_TAG,
                    TypeTags.DECIMAL_TAG, TypeTags.RECORD_TYPE_TAG, TypeTags.MAP_TAG, TypeTags.OBJECT_TYPE_TAG,
                    TypeTags.XML_TAG, TypeTags.NULL_TAG -> TypeCreator.createArrayType(type);
            case TypeTags.TYPE_REFERENCED_TYPE_TAG -> getArrayTypeFromElementType(TypeUtils.getReferredType(type));
            default -> PredefinedTypes.TYPE_ANYDATA_ARRAY;
        };
    }

    public static MapType getMapTypeFromConstraintType(Type constraintType) {
        return switch (constraintType.getTag()) {
            case TypeTags.MAP_TAG -> (MapType) constraintType;
            case TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.STRING_TAG, TypeTags.BOOLEAN_TAG, TypeTags.BYTE_TAG,
                    TypeTags.DECIMAL_TAG, TypeTags.JSON_TAG, TypeTags.RECORD_TYPE_TAG, TypeTags.OBJECT_TYPE_TAG,
                    TypeTags.XML_TAG, TypeTags.NULL_TAG -> TypeCreator.createMapType(constraintType);
            case TypeTags.ARRAY_TAG -> TypeCreator.createMapType(((ArrayType) constraintType).getElementType());
            case TypeTags.TYPE_REFERENCED_TYPE_TAG ->
                    getMapTypeFromConstraintType(TypeUtils.getReferredType(constraintType));
            default -> TypeCreator.createMapType(PredefinedTypes.TYPE_ANYDATA);
        };
    }

    public static void updateExpectedTypeStacks(RecordType recordType, XmlAnalyzerData analyzerData) {
        analyzerData.attributeHierarchy.push(new QualifiedNameMap(getAllAttributesInRecordType(recordType)));
        analyzerData.fieldHierarchy.push(new QualifiedNameMap(getAllFieldsInRecordType(recordType, analyzerData)));
        analyzerData.restTypes.push(recordType.getRestFieldType());
    }

    public static void removeExpectedTypeStacks(XmlAnalyzerData analyzerData) {
        analyzerData.attributeHierarchy.pop();
        analyzerData.fieldHierarchy.pop();
        analyzerData.restTypes.pop();
    }

    public static boolean isAnydataOrJson(int typeTag) {
        return typeTag == TypeTags.ANYDATA_TAG || typeTag == TypeTags.JSON_TAG;
    }

    public static boolean isSupportedType(Type type) {
        switch (type.getTag()) {
            case TypeTags.NULL_TAG, TypeTags.INT_TAG, TypeTags.BYTE_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG,
                    TypeTags.BOOLEAN_TAG, TypeTags.STRING_TAG, TypeTags.RECORD_TYPE_TAG, TypeTags.MAP_TAG,
                    TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
                return true;
            }
            case TypeTags.ARRAY_TAG -> {
                return isSupportedType(((ArrayType) type).getElementType());
            }
            case TypeTags.UNION_TAG -> {
                return isSupportedUnionType((UnionType) type);
            }
            case TypeTags.TYPE_REFERENCED_TYPE_TAG -> {
                return isSupportedType(TypeUtils.getReferredType(type));
            }
        }
        return false;
    }

    private static boolean isSupportedUnionType(UnionType type) {
        for (Type memberType : type.getMemberTypes()) {
            switch (memberType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG, TypeTags.OBJECT_TYPE_TAG, TypeTags.MAP_TAG, TypeTags.JSON_TAG,
                        TypeTags.ANYDATA_TAG, TypeTags.XML_TAG -> {
                    return false;
                }
                case TypeTags.UNION_TAG -> {
                    return !isSupportedUnionType(type);
                }
            }
        }
        return true;
    }

    public static void updateOptions(BMap<BString, Object> options, XmlAnalyzerData analyzerData) {
        analyzerData.attributePrefix = options.get(Constants.ATTRIBUTE_PREFIX).toString();
        analyzerData.textFieldName = options.get(Constants.TEXT_FIELD_NAME).toString();
        analyzerData.allowDataProjection = (boolean) options.get(Constants.ALLOW_DATA_PROJECTION);
    }

    public static void logArrayMismatchErrorIfProjectionNotAllowed(boolean allowDataProjection) {
        if (allowDataProjection) {
            return;
        }
        throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
    }

    @SuppressWarnings("unchecked")
    public static Object getModifiedRecord(BMap<BString, Object> input, BString textFieldName, BTypedesc type) {
        Type describingType = type.getDescribingType();
        contentFieldName = textFieldName.getValue();
        if (describingType.getTag() == TypeTags.MAP_TAG) {
            Type constraintType = TypeUtils.getReferredType(((MapType) describingType).getConstrainedType());
            switch (constraintType.getTag()) {
                case TypeTags.ARRAY_TAG -> {
                    return processArrayValue(input, (ArrayType) constraintType);
                }
                case TypeTags.MAP_TAG -> {
                    BMap<BString, Object> jsonMap =
                            ValueCreator.createMapValue(TypeCreator.createMapType(PredefinedTypes.TYPE_XML));
                    for (Map.Entry<BString, Object> entry : input.entrySet()) {
                        jsonMap.put(entry.getKey(), entry.getValue());
                    }
                    return jsonMap;
                }
                case TypeTags.UNION_TAG -> {
                    return DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE);
                }
            }
        }
        if (describingType.getTag() == TypeTags.RECORD_TYPE_TAG &&
                describingType.getFlags() != Constants.DEFAULT_TYPE_FLAG) {
            BArray jsonArray = ValueCreator.createArrayValue(PredefinedTypes.TYPE_JSON_ARRAY);
            BMap<BString, Object> recordField =  addFields(input, type.getDescribingType());
            BMap<BString, Object> processedRecord = processParentAnnotation(type.getDescribingType(), recordField);
            BString rootTagName = processedRecord.getKeys()[0];
            jsonArray.append(processedRecord.get(rootTagName));
            jsonArray.append(rootTagName);
            return jsonArray;
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    private static BMap<BString, Object> processArrayValue(BMap<BString, Object> input, ArrayType arrayType) {
        Type elementType = TypeUtils.getReferredType(arrayType.getElementType());
        switch (elementType.getTag()) {
            case TypeTags.RECORD_TYPE_TAG -> {
                BMap<BString, Object> jsonMap = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
                for (Map.Entry<BString, Object> entry : input.entrySet()) {
                    List<BMap<BString, Object>> records = new ArrayList<>();
                    BArray arrayValue = (BArray) entry.getValue();
                    for (int i = 0; i < arrayValue.getLength(); i++) {
                        BMap<BString, Object> record = addFields(((BMap<BString, Object>) arrayValue.get(i)),
                                elementType);
                        BMap<BString, Object> parentRecord = processParentAnnotation(elementType, record);
                        // Remove parent element
                        records.add((BMap<BString, Object>) parentRecord.get(parentRecord.getKeys()[0]));
                    }
                    jsonMap.put(entry.getKey(), ValueCreator.createArrayValue(records.toArray(),
                            TypeCreator.createArrayType(elementType)));
                }
                return jsonMap;
            }
            case TypeTags.XML_TAG -> {
                ArrayType xmlArrayType = TypeCreator.createArrayType(PredefinedTypes.TYPE_XML);
                BMap<BString, Object> jsonMap =
                        ValueCreator.createMapValue(TypeCreator.createMapType(xmlArrayType));
                for (Map.Entry<BString, Object> entry : input.entrySet()) {
                    BArray arrayValue = (BArray) entry.getValue();
                    BArray xmlArrayValue = ValueCreator.createArrayValue(xmlArrayType);
                    for (int i = 0; i < arrayValue.getLength(); i++) {
                        xmlArrayValue.append(arrayValue.get(i));
                    }
                    jsonMap.put(entry.getKey(), xmlArrayValue);
                }
                return jsonMap;
            }
            default -> {
                return input;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static BMap<BString, Object> addFields(BMap<BString, Object> input, Type type) {
        BMap<BString, Object> recordValue = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
        Map<String, Field> fields = ((RecordType) type).getFields();
        BMap<BString, Object> annotations = ((RecordType) type).getAnnotations();
        for (Map.Entry<BString, Object> entry: input.entrySet()) {
            String key = entry.getKey().getValue();
            Object value = entry.getValue();
            if (fields.containsKey(key)) {
                Type fieldType = fields.get(key).getFieldType();
                fieldType = getTypeFromUnionType(fieldType, value);
                if (fieldType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    processRecord(key, annotations, recordValue, value, fieldType);
                } else if (fieldType.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG) {
                    Type referredType = TypeUtils.getReferredType(fieldType);
                    if (annotations.size() > 0) {
                        key = getKeyNameFromAnnotation(annotations, key);
                    }
                    BMap<BString, Object> subRecordAnnotations = ((RecordType) referredType).getAnnotations();
                    key = getElementName(subRecordAnnotations, key);
                    BMap<BString, Object>  annotationRecord = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
                    processSubRecordAnnotation(subRecordAnnotations, annotationRecord);
                    BMap<BString, Object> subRecordValue = addFields(((BMap<BString, Object>) value), referredType);
                    if (annotationRecord.size() > 0) {
                        subRecordValue.put(annotationRecord.getKeys()[0],
                                annotationRecord.get(annotationRecord.getKeys()[0]));
                    }
                    recordValue.put(StringUtils.fromString(key), subRecordValue);
                } else if (fieldType.getTag() == TypeTags.ARRAY_TAG) {
                    processArray(fieldType, annotations, recordValue, entry);
                } else {
                    addPrimitiveValue(addFieldNamespaceAnnotation(key, annotations, recordValue),
                            annotations, recordValue, value);
                }
            } else {
                recordValue.put(StringUtils.fromString(key), value);
            }
        }
        return recordValue;
    }

    @SuppressWarnings("unchecked")
    private static QName addFieldNamespaceAnnotation(String key, BMap<BString, Object> annotations,
                                                     BMap<BString, Object> recordValue) {
        BString annotationKey = StringUtils.fromString(Constants.FIELD
                + (key.replaceAll(Constants.NON_NUMERIC_STRING_REGEX, "\\\\$0")));
        boolean isAttributeField = isAttributeField(annotationKey, annotations);
        if (annotations.containsKey(annotationKey)) {
            BMap<BString, Object> annotationValue = (BMap<BString, Object>) annotations.get(annotationKey);
            for (BString fieldKey : annotationValue.getKeys()) {
                if (fieldKey.toString().endsWith(Constants.NAMESPACE)) {
                    return processFieldNamespaceAnnotation(annotationValue, key, fieldKey, recordValue,
                            isAttributeField);
                }
            }
        }
        return new QName("", key, "");
    }

    @SuppressWarnings("unchecked")
    public static boolean isAttributeField(BString annotationKey, BMap<BString, Object> annotations) {
        if (!annotations.containsKey(annotationKey)) {
            return false;
        }

        BMap<BString, Object> annotationValue = (BMap<BString, Object>) annotations.get(annotationKey);
        for (BString fieldKey : annotationValue.getKeys()) {
            if (fieldKey.toString().endsWith(Constants.ATTRIBUTE)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static BMap<BString, Object> getFieldNamespaceAndNameAnnotations(String key,
                                                                             BMap<BString, Object> parentAnnotations) {
        BMap<BString, Object> nsFieldAnnotation = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
        BString annotationKey =
                StringUtils.fromString((Constants.FIELD
                        + (key.replaceAll(Constants.NON_NUMERIC_STRING_REGEX, "\\\\$0"))));
        if (!parentAnnotations.containsKey(annotationKey)) {
            return nsFieldAnnotation;
        }

        BMap<BString, Object> annotationValue = (BMap<BString, Object>) parentAnnotations.get(annotationKey);
        for (BString fieldKey : annotationValue.getKeys()) {
            String keyName = fieldKey.getValue();
            if (keyName.endsWith(Constants.NAMESPACE) || keyName.endsWith(Constants.NAME)) {
                nsFieldAnnotation.put(fieldKey, annotationValue.get(fieldKey));
                break;
            }
        }
        return nsFieldAnnotation;
    }

    @SuppressWarnings("unchecked")
    private static void processRecord(String key, BMap<BString, Object> parentAnnotations,
                                      BMap<BString, Object> record, Object value, Type childType) {
        BMap<BString, Object>  parentRecordAnnotations = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
        BMap<BString, Object> annotation = ((RecordType) childType).getAnnotations();
        if (parentAnnotations.size() > 0) {
            annotation.merge(getFieldNamespaceAndNameAnnotations(key, parentAnnotations), true);
            processSubRecordAnnotation(parentAnnotations, parentRecordAnnotations);
        }
        BMap<BString, Object> subRecord = addFields(((BMap<BString, Object>) value), childType);
        if (annotation.size() > 0) {
            processSubRecordAnnotation(annotation, subRecord);
        }
        key = getElementName(annotation, key);
        record.put(StringUtils.fromString(key), subRecord);
        if (parentRecordAnnotations.size() > 0) {
            record.put(parentRecordAnnotations.getKeys()[0],
                    parentRecordAnnotations.get(parentRecordAnnotations.getKeys()[0]));
        }
    }

    @SuppressWarnings("unchecked")
    private static void addPrimitiveValue(QName qName, BMap<BString, Object> annotations,
                                          BMap<BString, Object> record, Object value) {
        BString localPart = StringUtils.fromString(qName.getLocalPart());
        BString key = qName.getPrefix().isBlank() ?
                localPart : StringUtils.fromString(qName.getPrefix() + ":" + localPart);
        BString annotationKey = StringUtils.fromString(Constants.FIELD
                        + (localPart.getValue().replaceAll(Constants.NON_NUMERIC_STRING_REGEX, "\\\\$0")));
        BMap<BString, Object> currentValue;
        if (record.containsKey(key)) {
            currentValue = (BMap<BString, Object>) record.get(key);
            key = StringUtils.fromString(contentFieldName);
        } else {
            currentValue = record;
        }

        if (annotations.containsKey(annotationKey)) {
            BMap<BString, Object> annotationValue = (BMap<BString, Object>) annotations.get(annotationKey);
            currentValue.put(StringUtils.fromString(processFieldAnnotation(annotationValue, key.getValue())), value);
        } else {
            currentValue.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void processArray(Type childType, BMap<BString, Object> annotations,
                                     BMap<BString, Object> record, Map.Entry<BString, Object> entry) {
        Type elementType = TypeUtils.getReferredType(((ArrayType) childType).getElementType());
        BMap<BString, Object>  annotationRecord = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
        String keyName = entry.getKey().getValue();
        if (annotations.size() > 0) {
            keyName = getKeyNameFromAnnotation(annotations, keyName);
            processSubRecordAnnotation(annotations, annotationRecord);
        }
        BArray arrayValue = (BArray) entry.getValue();
        if (elementType.getTag() == TypeTags.RECORD_TYPE_TAG) {
            List<BMap<BString, Object>> records = new ArrayList<>();
            for (int i = 0; i < arrayValue.getLength(); i++) {
                BMap<BString, Object> subRecord = addFields(((BMap<BString, Object>) arrayValue.get(i)),
                        elementType);
                subRecord = processParentAnnotation(elementType, subRecord);
                records.add((BMap<BString, Object>) subRecord.get(subRecord.getKeys()[0]));
            }
            record.put(
                    StringUtils.fromString(getElementName(((RecordType) elementType).getAnnotations(), keyName)),
                    ValueCreator.createArrayValue(records.toArray(),
                            TypeCreator.createArrayType(Constants.JSON_ARRAY_TYPE)));
        } else {
            List<Object> records = new ArrayList<>();
            for (int i = 0; i < arrayValue.getLength(); i++) {
                records.add(arrayValue.get(i));
            }
            record.put(StringUtils.fromString(keyName), ValueCreator.createArrayValue(records.toArray(),
                    TypeCreator.createArrayType(Constants.JSON_ARRAY_TYPE)));
        }
        if (annotationRecord.size() > 0) {
            record.put(annotationRecord.getKeys()[0],
                    annotationRecord.get(annotationRecord.getKeys()[0]));
        }
    }

    @SuppressWarnings("unchecked")
    private static String getKeyNameFromAnnotation(BMap<BString, Object> annotations, String keyName) {
        BString annotationKey = StringUtils.fromString(Constants.FIELD
                + (keyName.replaceAll(Constants.NON_NUMERIC_STRING_REGEX, "\\\\$0")));
        if (annotations.containsKey(annotationKey)) {
            BMap<BString, Object> annotationValue = (BMap<BString, Object>) annotations.get(annotationKey);
            return processFieldAnnotation(annotationValue, keyName);
        }
        return keyName;
    }

    public static Type getTypeFromUnionType(Type childType, Object value) {
        if (childType instanceof UnionType bUnionType) {
            for (Type memberType : bUnionType.getMemberTypes()) {
                if (value.getClass().getName().toUpperCase(Locale.ROOT).contains(
                        memberType.getName().toUpperCase(Locale.ROOT))) {
                    childType = TypeUtils.getReferredType(memberType);
                }
            }
        }
        return childType;
    }

    private static BMap<BString, Object> processParentAnnotation(Type type, BMap<BString, Object> record) {
        BMap<BString, Object> parentRecord = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
        BMap<BString, Object> namespaces = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
        BMap<BString, Object> annotations = ((RecordType) type).getAnnotations();
        BString rootName = processAnnotation(annotations, type.getName(), namespaces);
        if (namespaces.size() > 0) {
            for (Map.Entry<BString, Object> namespace : namespaces.entrySet()) {
                record.put(namespace.getKey(), namespace.getValue());
            }
        }
        parentRecord.put(rootName, record);
        return parentRecord;
    }

    @SuppressWarnings("unchecked")
    private static String processFieldAnnotation(BMap<BString, Object> annotation, String key) {
        for (BString value : annotation.getKeys()) {
            String stringValue = value.getValue();
            if (stringValue.endsWith(Constants.NAME)) {
                BMap<BString, Object> names = (BMap<BString, Object>) annotation.get(value);
                String name = names.get(StringUtils.fromString(VALUE)).toString();
                if (key.contains(Constants.COLON)) {
                    key = key.substring(0, key.indexOf(Constants.COLON) + 1) + name;
                } else if (key.contains(ATTRIBUTE_PREFIX)) {
                    key = key.substring(0, key.indexOf(Constants.UNDERSCORE) + 1) + name;
                } else {
                    key = name;
                }
            }
            if (stringValue.endsWith(Constants.ATTRIBUTE)) {
                key = ATTRIBUTE_PREFIX.concat(key);
            }
        }
        return key;
    }

    private static BString processAnnotation(BMap<BString, Object> annotation, String key,
                                             BMap<BString, Object> namespaces) {
        boolean hasNamespaceAnnotation = false;
        for (BString value : annotation.getKeys()) {
            if (!value.getValue().contains(Constants.FIELD)) {
                String stringValue = value.getValue();
                if (stringValue.endsWith(Constants.NAME)) {
                    key = processNameAnnotation(annotation, key, value, hasNamespaceAnnotation);
                }
                if (stringValue.endsWith(Constants.NAMESPACE)) {
                    hasNamespaceAnnotation = true;
                    key = processNamespaceAnnotation(annotation, key, value, namespaces);
                }
            }
        }
        return StringUtils.fromString(key);
    }

    private static void processSubRecordAnnotation(BMap<BString, Object> annotation, BMap<BString, Object>  subRecord) {
        BString[] keys = annotation.getKeys();
        for (BString value : keys) {
            if (value.getValue().endsWith(Constants.NAMESPACE)) {
                processNamespaceAnnotation(annotation, "", value, subRecord);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String getElementName(BMap<BString, Object> annotation, String key) {
        BString[] keys = annotation.getKeys();
        boolean hasNamespaceAnnotation = false;
        for (BString value : keys) {
            if (value.getValue().endsWith(Constants.NAMESPACE)) {
                hasNamespaceAnnotation = true;
                BMap<BString, Object> namespaceAnnotation = (BMap<BString, Object>) annotation.get(value);
                BString prefix = (BString) namespaceAnnotation.get(Constants.PREFIX);
                if (prefix != null) {
                    key = prefix.getValue().concat(Constants.COLON).concat(key);
                }
            }
            if (value.getValue().endsWith(Constants.NAME)) {
                key = processNameAnnotation(annotation, key, value, hasNamespaceAnnotation);
            }
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private static String processNameAnnotation(BMap<BString, Object> annotation, String key, BString value,
                                                boolean hasNamespaceAnnotation) {
        String nameValue = ((BMap<BString, Object>) annotation.get(value)).
                get(StringUtils.fromString(VALUE)).toString();
        if (hasNamespaceAnnotation) {
            return key.substring(0, key.indexOf(Constants.COLON) + 1) + nameValue;
        } else {
            return nameValue;
        }
    }

    @SuppressWarnings("unchecked")
    private static String processNamespaceAnnotation(BMap<BString, Object> annotation, String key, BString value,
                                                     BMap<BString, Object>  subRecord) {
        BMap<BString, Object> namespaceAnnotation = (BMap<BString, Object>) annotation.get(value);
        BString uri = (BString) namespaceAnnotation.get(Constants.URI);
        BString prefix = (BString) namespaceAnnotation.get(Constants.PREFIX);
        if (prefix ==  null) {
            subRecord.put(StringUtils.fromString(ATTRIBUTE_PREFIX + "xmlns"), uri);
        } else {
            subRecord.put(StringUtils.fromString(ATTRIBUTE_PREFIX + "xmlns:" + prefix), uri);
            key = prefix.getValue().concat(Constants.COLON).concat(key);
        }
        return key;
    }

    @SuppressWarnings("unchecked")
    private static QName processFieldNamespaceAnnotation(BMap<BString, Object> annotation, String key, BString value,
                                                         BMap<BString, Object>  subRecord, boolean isAttributeField) {
        BMap<BString, Object> namespaceAnnotation = (BMap<BString, Object>) annotation.get(value);
        BString uri = (BString) namespaceAnnotation.get(Constants.URI);
        BString prefix = (BString) namespaceAnnotation.get(Constants.PREFIX);
        QName qName = new QName(uri == null ? "" : uri.getValue(), key, prefix == null ? "" : prefix.getValue());
        if (isAttributeField) {
            addAttributeToRecord(prefix, uri, key, subRecord);
        } else {
            BMap<BString, Object> nextMapValue = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
            key = addAttributeToRecord(prefix, uri, key, nextMapValue);
            subRecord.put(StringUtils.fromString(key), nextMapValue);
        }
        return qName;
    }

    private static String addAttributeToRecord(BString prefix, BString uri, String key,
                                               BMap<BString, Object> subRecord) {
        if (prefix ==  null) {
            subRecord.put(StringUtils.fromString(ATTRIBUTE_PREFIX + "xmlns"), uri);
            return key;
        }
        subRecord.put(StringUtils.fromString(ATTRIBUTE_PREFIX + "xmlns:" + prefix), uri);
        return prefix.getValue().concat(Constants.COLON).concat(key);
    }

    public static Object validateConstraints(Object convertedValue, BTypedesc typed, boolean requireValidation) {
        if (!requireValidation) {
            return convertedValue;
        }

        Object result = Constraints.validate(convertedValue, typed);
        if (result instanceof BError bError) {
             return DiagnosticLog.createXmlError(getPrintableErrorMsg(bError));
        }
        return convertedValue;
    }

    private static String getPrintableErrorMsg(BError err) {
        String errorMsg = err.getMessage() != null ? err.getMessage() : "";
        Object details = err.getDetails();
        if (details != null && !details.toString().equals("{}")) {
            errorMsg += ", " + details;
        }
        return errorMsg;
    }

    /**
     * Holds data required for the traversing.
     *
     * @since 0.1.0
     */
    public static class XmlAnalyzerData {
        public final Stack<Object> nodesStack = new Stack<>();
        public final Stack<QualifiedNameMap<Field>> fieldHierarchy = new Stack<>();
        public final Stack<QualifiedNameMap<Field>> attributeHierarchy = new Stack<>();
        public final Stack<Type> restTypes = new Stack<>();
        public RecordType rootRecord;
        public Field currentField;
        public QualifiedName rootElement;
        public String attributePrefix;
        public String textFieldName;
        public boolean allowDataProjection;
    }
}
