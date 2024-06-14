// Copyright (c) 2023, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/jballerina.java;

const XMLNS_NAMESPACE_URI = "http://www.w3.org/2000/xmlns/";
const CONTENT = "#content";
const ATTRIBUTE_PREFIX = "attribute_";
const XMLNS = "xmlns";
const EMPTY_STRING = "";

# Defines the name of the XML element.
#
# + value - The name of the XML element
public type NameConfig record {|
    string value;
|};

# The annotation is used to specify the new name of the existing record name or field name according to the XML format.
# When using `parseString`, `parseBytes`, `parseStream`, `parseAsType`, this annotation can be used to 
# validate the name of the XML element with the record field or type.
# When using `toXml`, this annotation can be used to override the name of field or type.
public const annotation NameConfig Name on type, record field;

# Defines the namespace of the XML element.
#
# + prefix - The value of the prefix of the namespace
# + uri - The value of the URI of the namespace
public type NamespaceConfig record {|
    string prefix?;
    string uri;
|};

# The annotation is used to specify the namespace's prefix and URI of the XML element.
# When using `parseString`, `parseBytes`, `parseStream`, `parseAsType`, this annotation can be used to 
# validate the namespace of the XML element with the record field or type.
# When using `toXml`, this annotation can be used to add the namespace to the XML element.
public const annotation NamespaceConfig Namespace on type, record field;

# The annotation is used to denote the field that is considered an attribute.
# When using `parseString`, `parseBytes`, `parseStream`, `parseAsType`, this annotation can be used to 
# indicate the record field as an attribute.
# When using `toXml`, this annotation can be used to add the attribute to the XML element.
public const annotation Attribute on record field;

# Represent the options that can be used to modify the behaviour of projection.
#
# + attributePrefix - prefix added for attribute fields in the record
# + textFieldName - field name for the text field
public type Options record {|
    string attributePrefix = EMPTY_STRING;
    string textFieldName = "#content";
|};

# Represent the options that can be used to modify the behaviour of projection.
#
# + allowDataProjection - enable or disable projection
# + enableConstraintValidation - enable or disable constraint validation
public type SourceOptions record {|
    *Options;
    boolean allowDataProjection = true;
    boolean enableConstraintValidation = true;
|};

# Represents the error type of the ballerina/data.xmldata module. This error type represents any error that can occur
# during the execution of xmldata APIs.
public type Error distinct error;

# Converts XML to record type with projection.
#
# + v - Source XML value 
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `xmldata:Error` value
public isolated function parseAsType(xml v, SourceOptions options = {}, typedesc<record {}> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.lib.data.xmldata.xml.Native"} external;

# Converts XML string to record type with projection.
#
# + s - Source XML string value
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `xmldata:Error` value
public isolated function parseString(string s, SourceOptions options = {}, typedesc<record {}> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.lib.data.xmldata.xml.Native"} external;

# Converts XML byte[] to record type with projection.
#
# + s - Source XML  byte[]
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `xmldata:Error` value
public isolated function parseBytes(byte[] s, SourceOptions options = {}, typedesc<record {}> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.lib.data.xmldata.xml.Native"} external;

# Converts XML byte-block-stream to record type with projection.
#
# + s - Source XML byte-block-stream
# + options - Options to be used for filtering in the projection
# + t - Target type to be used for filtering in the projection
# + return - On success, returns the given target type value, else returns an `xmldata:Error` value
public isolated function parseStream(stream<byte[], error?> s, SourceOptions options = {}, typedesc<record {}> t = <>)
        returns t|Error = @java:Method {'class: "io.ballerina.lib.data.xmldata.xml.Native"} external;

# Converts a `Map` or `Record` representation to its XML representation.
# Additionally, when converting from a record, the `xmldata:Namespace`, `xmldata:Name`, and `xmldata:Attribute`
# annotations can be used to add `namespaces`, `name of elements`, and `attributes` to XML representation.
#
# + mapValue - The `Map` or `Record` representation source to be converted to XML
# + options - Options to be used for filtering in the projection
# + return - XML representation of the given source if the source is
# successfully converted or else an `xmldata:Error`
public isolated function toXml(map<anydata> mapValue, Options options = {}) returns xml|Error {
    string textFieldName = options.textFieldName;
    JsonOptions jsonOptions = {
        attributePrefix: ATTRIBUTE_PREFIX,
        arrayEntryTag: EMPTY_STRING,
        textFieldName,
        userAttributePrefix: options.attributePrefix
    };
    typedesc<(map<anydata>)> inputType = typeof mapValue;
    json|record {} jsonValue = check getModifiedRecord(mapValue, textFieldName, inputType);
    if jsonValue is map<xml>|map<xml[]> {
        return convertMapXml(jsonValue);
    } else if jsonValue is json[] {
        jsonOptions.rootTag = jsonValue[1].toString();
        return fromJson(jsonValue[0], jsonOptions);
    }
    return fromJson(jsonValue.toJson(), jsonOptions);
}

isolated function convertMapXml(map<xml>|map<xml[]> mapValue) returns xml {
    xml xNode = xml ``;
    foreach [string, xml|xml[]] [key, xmlVal] in mapValue.entries() {
        xml|xml[] values = xmlVal;
        if values is xml[] {
            foreach xml value in values {
                xNode += xml:createElement(key, {}, value);
            }
        } else {
            xNode += xml:createElement(key, {}, values);
        }
    }
    return xml:createElement("root", {}, xNode);
}

isolated function getModifiedRecord(map<anydata> mapValue, string textFieldName, typedesc<(map<anydata>|json)> inputType)
    returns json|record {}|Error = @java:Method {'class: "io.ballerina.lib.data.xmldata.utils.DataUtils"} external;

# Provides configurations for converting JSON to XML.
#
# + attributePrefix - The prefix of JSON elements' key which is to be treated as an attribute in the XML representation
# + arrayEntryTag - The name of the XML elements that represent a converted JSON array entry
# + rootTag - The name of the root element of the XML that will be created. If its value is (), and the converted XML
# is not in the valid format, it will create a root tag as `root`
# + textFieldName - field name for the text field
# + userAttributePrefix - The prefix of JSON elements' key which used by the user to distinguish the attribute fields
type JsonOptions record {|
    string attributePrefix = "@";
    string arrayEntryTag = "item";
    string? rootTag = ();
    string textFieldName = CONTENT;
    string userAttributePrefix = EMPTY_STRING;
|};

# Converts a JSON object to an XML representation.
#
# + jsonValue - The JSON source to be converted to XML
# + options - The `xmldata:JsonOptions` record for JSON to XML conversion properties
# + return - XML representation of the given JSON if the JSON is successfully converted or else an `xmldata:Error`.
isolated function fromJson(json jsonValue, JsonOptions options = {}) returns xml|Error {
    string? rootTag = options.rootTag;
    map<string> allNamespaces = {};
    if !isSingleNode(jsonValue) {
        addNamespaces(allNamespaces, check getNamespacesMap(jsonValue, options, {}));
        return getElement(rootTag ?: "root",
                        check traverseNode(jsonValue, allNamespaces, {}, options), allNamespaces, options,
                        check getAttributesMap(jsonValue, options, allNamespaces));
    }

    map<json>|error jMap = jsonValue.ensureType();
    if jMap is map<json> {
        if jMap.length() == 0 {
            return xml ``;
        }

        json value = jMap.toArray()[0];
        addNamespaces(allNamespaces, check getNamespacesMap(value, options, {}));
        if value is json[] {
            return getElement(rootTag ?: "root",
                                check traverseNode(value, allNamespaces, {}, options, jMap.keys()[0]),
                                allNamespaces, options, check getAttributesMap(value, options, allNamespaces));
        }

        string key = jMap.keys()[0];
        if key == options.textFieldName {
            return xml:createText(value.toString());
        }
        xml output = check getElement(jMap.keys()[0], check traverseNode(value, allNamespaces, {}, options),
                                    allNamespaces, options,
                                    check getAttributesMap(value, options, allNamespaces));
        if rootTag is string {
            return xml:createElement(rootTag, {}, output);
        }
        return output;
    }
    return jsonValue is null ? xml `` : xml:createText(jsonValue.toString());
}

isolated function traverseNode(json jNode, map<string> allNamespaces, map<string> parentNamespaces, JsonOptions options,
        string? 'key = ()) returns xml|Error {
    map<string> namespacesOfElem = {};
    string attributePrefix = options.attributePrefix;
    xml xNode = xml ``;
    if jNode is map<json> {
        foreach [string, json] [k, value] in jNode.entries() {
            string jsonKey = k.trim();
            if jsonKey.startsWith(attributePrefix) {
                continue;
            }

            if jsonKey == options.textFieldName {
                xNode += xml:createText(value.toString());
            } else {
                namespacesOfElem = check getNamespacesMap(value, options, parentNamespaces);
                addNamespaces(allNamespaces, namespacesOfElem);
                if value is json[] {
                    xNode += check traverseNode(value, allNamespaces, namespacesOfElem, options, jsonKey);
                } else {
                    xNode +=
                    check getElement(jsonKey, check traverseNode(value, allNamespaces, namespacesOfElem, options),
                                    allNamespaces, options,
                                    check getAttributesMap(value, options, allNamespaces, parentNamespaces));
                }
            }
        }
    } else if jNode is json[] {
        foreach var i in jNode {
            string arrayEntryTagKey = EMPTY_STRING;
            if 'key is string {
                arrayEntryTagKey = 'key;
            } else if options.arrayEntryTag != EMPTY_STRING {
                arrayEntryTagKey = options.arrayEntryTag;
            }
            namespacesOfElem = check getNamespacesMap(i, options, parentNamespaces);
            addNamespaces(allNamespaces, namespacesOfElem);
            if options.arrayEntryTag == EMPTY_STRING {
                xNode += check getElement(arrayEntryTagKey,
                                        check traverseNode(i, allNamespaces, namespacesOfElem, options, 'key),
                                        allNamespaces, options,
                                        check getAttributesMap(i, options, allNamespaces, parentNamespaces));
            } else {
                xNode += check getElement(arrayEntryTagKey,
                                        check traverseNode(i, allNamespaces, namespacesOfElem, options),
                                        allNamespaces, options,
                                        check getAttributesMap(i, options, allNamespaces, parentNamespaces));
            }
        }
    } else {
        xNode = xml:createText(jNode.toString());
    }
    return xNode;
}

isolated function isSingleNode(json node) returns boolean {
    map<anydata>|error jMap = node.ensureType();
    return node !is json[] && (jMap !is map<anydata> || jMap.length() <= 1);
}

isolated function getElement(string name, xml children, map<string> namespaces, JsonOptions options,
        map<string> attributes = {}) returns xml|Error {
    string attributePrefix = options.attributePrefix;
    string userAttributePrefix = options.userAttributePrefix;
    xml:Element element;
    int? index = name.indexOf(":");
    if index is int {
        string prefix = name.substring(0, index);

        string elementName;
        if userAttributePrefix !is EMPTY_STRING {
            elementName = removeUserAttributePrefix(name, userAttributePrefix, index);
        } else {
            elementName = name.substring(index + 1, name.length());
        }

        string namespaceUrl = attributes[string `{${XMLNS_NAMESPACE_URI}}${prefix}`].toString();
        if namespaceUrl == EMPTY_STRING {
            namespaceUrl = namespaces[string `{${XMLNS_NAMESPACE_URI}}${prefix}`].toString();
            if namespaceUrl != EMPTY_STRING {
                attributes[string `{${XMLNS_NAMESPACE_URI}}${prefix}`] = namespaceUrl;
            }
        }
        if namespaceUrl == EMPTY_STRING {
            element = xml:createElement(elementName, attributes, children);
        } else {
            element = xml:createElement(string `{${namespaceUrl}}${elementName}`, attributes, children);
        }
    } else {
        if name.startsWith(attributePrefix) {
            return error("attribute cannot be an object or array");
        }
        map<string> newAttributes = attributes;
        if newAttributes.hasKey(string `{${XMLNS_NAMESPACE_URI}}`) {
            string value = newAttributes.get(string `{${XMLNS_NAMESPACE_URI}}`);
            _ = newAttributes.remove(string `{${XMLNS_NAMESPACE_URI}}`);
            newAttributes[XMLNS] = value;
        }
        if userAttributePrefix !is EMPTY_STRING {
            element = xml:createElement(removeUserAttributePrefix(name, userAttributePrefix, ()), newAttributes, children);
        } else {
            element = xml:createElement(name, newAttributes, children);
        }
    }
    return element;
}

isolated function removeUserAttributePrefix(string name, string userAttributePrefix, int? index) returns string {
    int? usrAttIndex = name.indexOf(userAttributePrefix);
    if usrAttIndex is int {
        return name.substring(usrAttIndex + 1, name.length());
    }

    if index is int {
        return name.substring(index + 1, name.length());
    }
    return name;
}

isolated function getAttributesMap(json jTree, JsonOptions options, map<string> namespaces,
        map<string> parentNamespaces = {}) returns map<string>|Error {
    map<string> attributes = parentNamespaces.clone();
    map<json>|error attr = jTree.ensureType();
    string attributePrefix = options.attributePrefix;
    if attr !is map<json> {
        return attributes;
    }

    foreach [string, json] [k, v] in attr.entries() {
        if !k.startsWith(attributePrefix) {
            continue;
        }

        if v is map<json> || v is json[] {
            return error("attribute cannot be an object or array");
        }

        int? index = k.indexOf(":");
        if index is int {
            string suffix = k.substring(index + 1);
            if k.startsWith(attributePrefix + XMLNS) {
                attributes[string `{${XMLNS_NAMESPACE_URI}}${suffix}`] = v.toString();
            } else {
                int startIndex = getStartIndex(attributePrefix, options.userAttributePrefix, k);
                string prefix = k.substring(startIndex, index);
                string namespaceUrl = namespaces.get(string `{${XMLNS_NAMESPACE_URI}}${prefix}`);
                attributes[string `{${namespaceUrl}}${suffix}`] = v.toString();
            }
        } else {
            if k == attributePrefix + XMLNS {
                attributes[XMLNS] = v.toString();
            } else {
                int startIndex = getStartIndex(attributePrefix, options.userAttributePrefix, k);
                attributes[k.substring(startIndex)] = v.toString();
            }
        }
    }
    return attributes;
}

isolated function getStartIndex(string attributePrefix, string userAttributePrefix, string 'key) returns int {
    int startIndex = 1;
    if attributePrefix !is ATTRIBUTE_PREFIX {
        return startIndex;
    }

    int? location = userAttributePrefix is EMPTY_STRING ? 'key.indexOf("_") : 'key.indexOf(userAttributePrefix);
    if location is int {
        startIndex = location + 1;
    }
    return startIndex;
}

isolated function getNamespacesMap(json jTree, JsonOptions options, map<string> parentNamespaces = {})
                            returns map<string>|Error {
    map<string> namespaces = parentNamespaces.clone();
    map<json>|error attr = jTree.ensureType();
    string attributePrefix = options.attributePrefix;
    if attr !is map<json> {
        return namespaces;
    }

    foreach [string, json] [k, v] in attr.entries() {
        if !k.startsWith(attributePrefix) {
            continue;
        }

        if v is map<json>|json[] {
            return error("attribute cannot be an object or array.");
        }

        if !k.startsWith(attributePrefix + XMLNS) {
            continue;
        }

        int? index = k.indexOf(":");
        if index is int {
            string prefix = k.substring(index + 1);
            namespaces[string `{${XMLNS_NAMESPACE_URI}}${prefix}`] = v.toString();
        } else {
            namespaces[string `{${XMLNS_NAMESPACE_URI}}`] = v.toString();
        }
    }
    return namespaces;
}

isolated function addNamespaces(map<string> allNamespaces, map<string> namespaces) {
    foreach [string, string] ['key, namespace] in namespaces.entries() {
        allNamespaces['key] = namespace;
    }
}
