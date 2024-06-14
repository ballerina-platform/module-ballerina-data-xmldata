// Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
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
// under the License

import ballerina/constraint;
import ballerina/test;
import ballerina/time;

public type ValidationPerson record {|
    @constraint:String {
        maxLength: 5,
        minLength: 2
    }
    string name;
    @constraint:Int {
        minValueExclusive: 5
    }
    int age;
    @constraint:Float {
        minValue: 150,
        maxValue: 185.20,
        maxFractionDigits: 2
    }
    float height;
    @constraint:Date {
        option: {
            value: "PAST",
            message: "Date of birth should be past value"
            }, 
        message: "Invalid date found for date of birth"
    }
    time:Date dob;
    Family family;
|};

public type Family record {|
    @constraint:Array {
        maxLength: 2,
        minLength: 1
    }
    string[] members;
    @constraint:Int {
        maxDigits: 4
    }
    int id;
|};

@test:Config {
    groups: ["constraint-validation"]
}
function testValidConstraintAnnotationForParseString() returns error? {
    string xmlStr = string `
    <Person>
        <name>John</name>
        <age>6</age>
        <height>180.20</height>
        <dob>
            <year>1990</year>
            <month>12</month>
            <day>31</day>
        </dob>
        <family>
            <id>2221</id>
            <members>John</members>
            <members>Doe</members>
        </family>
    </Person>
    `;

    ValidationPerson person = check parseString(xmlStr);
    test:assertEquals(person.name, "John");
    test:assertEquals(person.age, 6);
    test:assertEquals(person.height, 180.20);
    test:assertEquals(person.dob.year, 1990);
    test:assertEquals(person.dob.month, 12);
    test:assertEquals(person.dob.day, 31);
    test:assertEquals(person.family.id, 2221);
    test:assertEquals(person.family.members.length(), 2); 
    test:assertEquals(person.family.members[0], "John"); 
    test:assertEquals(person.family.members[1], "Doe"); 
}

@constraint:Array {
    length: 2
}
public type Weight decimal[];

public type ValidationItem record {|
    Weight weight;
|};

@test:Config {
    groups: ["constraint-validation"],
    dataProvider: invalidConstraintAnnotation
}
function testInvalidConstraintAnnotationForParseString(string sourceData, typedesc<record {}> expType, string expectedError) {
    anydata|Error err = parseString(sourceData, {}, expType);
    test:assertEquals(err is Error, true);
    test:assertEquals((<error> err).message(), expectedError);
}

function invalidConstraintAnnotation() returns [string, typedesc<record {}>, string][] {
    return [
        [
            string `
                <Person>
                    <name>John Doe</name>
                    <age>6</age>
                    <height>180.20</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.name:maxLength' constraint(s)."
        ],
        [
            string `
                <Person>
                    <name>John</name>
                    <age>4</age>
                    <height>180.20</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.age:minValueExclusive' constraint(s)."
        ],
        [
            string `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>185.21</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.height:maxValue' constraint(s)."
        ],
        [
            string `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>167.252</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.height:maxFractionDigits' constraint(s)."
        ], 
        [
            string `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>167.25</height>
                    <dob>
                        <year>5000</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Date of birth should be past value."
        ],
        [
            string `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>167.25</height>
                    <dob>
                        <year>1999</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>22213</id>
                        <members>John</members>
                        <members>Doe</members>
                        <members>Ross</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.family.id:maxDigits','$.family.members:maxLength' constraint(s)."
        ],
        [
            string `
                <Item>
                    <weight>1.2</weight>
                    <weight>2.3</weight>
                    <weight>3.4</weight>
                </Item>
                `,
            ValidationItem,
            "Validation failed for '$.weight:length' constraint(s)."
        ]
    ];
}

@test:Config {
    groups: ["constraint-validation"]
}
function testValidConstraintAnnotationForParseAsType() returns error? {
    xml xmlVal = xml `
    <Person>
        <name>John</name>
        <age>6</age>
        <height>180.20</height>
        <dob>
            <year>1990</year>
            <month>12</month>
            <day>31</day>
        </dob>
        <family>
            <id>2221</id>
            <members>John</members>
            <members>Doe</members>
        </family>
    </Person>
    `;

    ValidationPerson person = check parseAsType(xmlVal);
    test:assertEquals(person.name, "John");
    test:assertEquals(person.age, 6);
    test:assertEquals(person.height, 180.20);
    test:assertEquals(person.dob.year, 1990);
    test:assertEquals(person.dob.month, 12);
    test:assertEquals(person.dob.day, 31);
    test:assertEquals(person.family.members.length(), 2); 
    test:assertEquals(person.family.members[0], "John"); 
    test:assertEquals(person.family.members[1], "Doe"); 
}

@test:Config {
    groups: ["constraint-validation"],
    dataProvider: invalidConstraintAnnotationForParseAsType
}
function testInvalidConstraintAnnotationForParseAsType(xml sourceData, typedesc<record {}> expType, string expectedError) {
    anydata|Error err = parseAsType(sourceData, {}, expType);
    test:assertEquals(err is Error, true);
    test:assertEquals((<error> err).message(), expectedError);
}

function invalidConstraintAnnotationForParseAsType() returns [xml, typedesc<record {}>, string][] {
    return [
        [
            xml `
                <Person>
                    <name>John Doe</name>
                    <age>6</age>
                    <height>180.20</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.name:maxLength' constraint(s)."
        ],
        [
            xml `
                <Person>
                    <name>John</name>
                    <age>4</age>
                    <height>180.20</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family> 
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.age:minValueExclusive' constraint(s)."
        ],
        [
            xml `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>185.21</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.height:maxValue' constraint(s)."
        ],
        [
            xml `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>167.252</height>
                    <dob>
                        <year>1990</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.height:maxFractionDigits' constraint(s)."
        ], 
        [
            xml `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>167.25</height>
                    <dob>
                        <year>5000</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>2221</id>
                        <members>John</members>
                        <members>Doe</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Date of birth should be past value."
        ],
        [
            xml `
                <Person>
                    <name>John</name>
                    <age>6</age>
                    <height>167.25</height>
                    <dob>
                        <year>1999</year>
                        <month>12</month>
                        <day>31</day>
                    </dob>
                    <family>
                        <id>22213</id>
                        <members>John</members>
                        <members>Doe</members>
                        <members>Ross</members>
                    </family>
                </Person>
                `,
            ValidationPerson,
            "Validation failed for '$.family.id:maxDigits','$.family.members:maxLength' constraint(s)."
        ],
        [
            xml `
                <Item>
                    <weight>1.2</weight>
                    <weight>2.3</weight>
                    <weight>3.4</weight>
                </Item>
                `,
            ValidationItem,
            "Validation failed for '$.weight:length' constraint(s)."
        ]
    ];
}
