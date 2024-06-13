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

import ballerina/test;

type Data record {|
    record {|
        int[] B;
        string C;
    |}[] A;
    string D;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord1() returns error? {
    string xmlStr = string `<Data>
        <A>
            <B>1</B>
            <B>2</B>
            <C>6</C>
        </A>
        <D>5</D>
        <A>
            <B>3</B>
            <B>4</B>
            <C>5</C>
        </A>
    </Data>`;
    Data rec1 = check parseString(xmlStr, {});

    test:assertEquals(rec1.A.length(), 2);
    test:assertEquals(rec1.A[0].B.length(), 2);
    test:assertEquals(rec1.A[0].B[0], 1);
    test:assertEquals(rec1.A[0].B[1], 2);
    test:assertEquals(rec1.A[0].C, "6");

    test:assertEquals(rec1.A[1].B.length(), 2);
    test:assertEquals(rec1.A[1].B[0], 3);
    test:assertEquals(rec1.A[1].B[1], 4);
    test:assertEquals(rec1.A[1].C, "5");

    test:assertEquals(rec1.D, "5");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord1() returns error? {
    xml xmlVal = xml `<Data><A><B>1</B><B>2</B><C>6</C></A><D>5</D><A><B>3</B><B>4</B><C>5</C></A></Data>`;
    Data rec1 = check parseAsType(xmlVal, {});

    test:assertEquals(rec1.A.length(), 2);
    test:assertEquals(rec1.A[0].B.length(), 2);
    test:assertEquals(rec1.A[0].B[0], 1);
    test:assertEquals(rec1.A[0].B[1], 2);
    test:assertEquals(rec1.A[0].C, "6");

    test:assertEquals(rec1.A[1].B.length(), 2);
    test:assertEquals(rec1.A[1].B[0], 3);
    test:assertEquals(rec1.A[1].B[1], 4);
    test:assertEquals(rec1.A[1].C, "5");

    test:assertEquals(rec1.D, "5");
}

type Data1 record {|
    record {|
        string \#content;
    |} A;
|};

type Data2 record {|
    string A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord2() returns error? {
    string xmlStr1 = "<Data1><A>1</A></Data1>";
    Data1 rec1 = check parseString(xmlStr1, {});
    test:assertEquals(rec1.A.\#content, "1");

    string xmlStr2 = "<Data2><A>1</A></Data2>";
    Data2 rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.A, "1");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord2() returns error? {
    xml xmlVal1 = xml `<Data1><A>1</A></Data1>`;
    Data1 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.\#content, "1");

    xml xmlVal2 = xml `<Data2><A>1</A></Data2>`;
    Data2 rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.A, "1");
}

type Data3 record {|
    record {|
        string \#content;
    |} A;
    record {|
        int \#content;
    |} B;
|};

@Name {
    value: "Data3"
}
type Data4 record {|
    string A;
    int B;
|};

type Data5 record {|
    record {|
        record {} B;
    |}[] A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord3() returns error? {
    string xmlStr1 = "<Data3><A>1</A><B>2</B></Data3>";
    Data3 rec1 = check parseString(xmlStr1, {});
    test:assertEquals(rec1.A.\#content, "1");
    test:assertEquals(rec1.B.\#content, 2);

    string xmlStr2 = "<Data3><A>1</A><B>2</B></Data3>";
    Data4 rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.A, "1");
    test:assertEquals(rec2.B, 2);

    string xmlStr3 = "<Data5><A><B>1</B></A><A><B>2</B></A><A><B>3</B></A></Data5>";
    Data5 rec3 = check parseString(xmlStr3);
    test:assertEquals(rec3.A.length(), 3);
    test:assertEquals(rec3.A[0].B.get("#content"), 1);
    test:assertEquals(rec3.A[1].B.get("#content"), 2);
    test:assertEquals(rec3.A[2].B.get("#content"), 3);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord3() returns error? {
    xml xmlval1 = xml `<Data3><A>1</A><B>2</B></Data3>`;
    Data3 rec1 = check parseAsType(xmlval1);
    test:assertEquals(rec1.A.\#content, "1");
    test:assertEquals(rec1.B.\#content, 2);

    xml xmlVal2 = xml `<Data3><A>1</A><B>2</B></Data3>`;
    Data4 rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.A, "1");
    test:assertEquals(rec2.B, 2);

    xml xmlVal3 = xml `<Data5><A><B>1</B></A><A><B>2</B></A><A><B>3</B></A></Data5>`;
    Data5 rec3 = check parseAsType(xmlVal3);
    test:assertEquals(rec3.A.length(), 3);
    test:assertEquals(rec3.A[0].B.get("#content"), 1);
    test:assertEquals(rec3.A[1].B.get("#content"), 2);
    test:assertEquals(rec3.A[2].B.get("#content"), 3);
}

type Data6 record {|
    record {|
        record {|
            record {|
                int[] D;
            |}[] C;
        |} D;
    |} A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord4() returns error? {
    string xmlStr1 = "<Data6><A><D><C><D>1</D><D>2</D></C><C><D>3</D><D>4</D></C></D></A></Data6>";
    Data6 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.D.C.length(), 2);
    test:assertEquals(rec1.A.D.C[0].D.length(), 2);
    test:assertEquals(rec1.A.D.C[0].D[0], 1);
    test:assertEquals(rec1.A.D.C[0].D[1], 2);
    test:assertEquals(rec1.A.D.C[1].D.length(), 2);
    test:assertEquals(rec1.A.D.C[1].D[0], 3);
    test:assertEquals(rec1.A.D.C[1].D[1], 4);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord4() returns error? {
    xml xmlVal1 = xml `<Data6><A><D><C><D>1</D><D>2</D></C><C><D>3</D><D>4</D></C></D></A></Data6>`;
    Data6 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.D.C.length(), 2);
    test:assertEquals(rec1.A.D.C[0].D.length(), 2);
    test:assertEquals(rec1.A.D.C[0].D[0], 1);
    test:assertEquals(rec1.A.D.C[0].D[1], 2);
    test:assertEquals(rec1.A.D.C[1].D.length(), 2);
    test:assertEquals(rec1.A.D.C[1].D[0], 3);
    test:assertEquals(rec1.A.D.C[1].D[1], 4);
}

type Data7 record {|
    record {|
        string C;
    |}[] A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord5() returns error? {
    string xmlStr1 = string `
    <Data>
        <A>
            <C>1</C>
        </A>
        <A>
            <C>2</C>
        </A>
    </Data>`;
    Data7 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.length(), 2);
    test:assertEquals(rec1.A[0].C, "1");
    test:assertEquals(rec1.A[1].C, "2");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord5() returns error? {
    xml xmlVal1 = xml `<Data>
        <A>
            <C>1</C>
        </A>
        <A>
            <C>2</C>
        </A>
    </Data>`;
    Data7 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.length(), 2);
    test:assertEquals(rec1.A[0].C, "1");
    test:assertEquals(rec1.A[1].C, "2");
}

type Data8 record {|
    int A;
    int[] B;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord9() returns error? {
    string xmlStr1 = string `
    <Data>
        <A>1</A>
        <B>1</B>
        <B>2</B>
    </Data>`;
    Data8 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A, 1);
    test:assertEquals(rec1.B.length(), 2);
    test:assertEquals((<int[]>rec1.B)[0], 1);
    test:assertEquals((<int[]>rec1.B)[1], 2);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord9() returns error? {
    xml xmlVal = xml `
    <Data>
        <A>1</A>
        <B>1</B>
        <B>2</B>
    </Data>`;
    Data8 rec1 = check parseAsType(xmlVal);
    test:assertEquals(rec1.A, 1);
    test:assertEquals(rec1.B.length(), 2);
    test:assertEquals((<int[]>rec1.B)[0], 1);
    test:assertEquals((<int[]>rec1.B)[1], 2);
}

// test for name annotations

@Name {
    value: "Data7"
}
type Rec1 record {|
    record {
    } A;
    record {
        float \#content;
    }[] B;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord6() returns error? {
    string xmlStr1 = string `<Data7>
        <A>1</A>
        <B>1.0</B>
        <B>2.0</B>
    </Data7>`;
    Rec1 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.get("#content"), 1);
    test:assertEquals(rec1.B.length(), 2);
    test:assertEquals(rec1.B[0].get("#content"), 1.0);
    test:assertEquals(rec1.B[1].get("#content"), 2.0);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord6() returns error? {
    xml xmlVal1 = xml `<Data7>
        <A>1</A>
        <B>1.0</B>
        <B>2.0</B>
    </Data7>`;
    Rec1 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.get("#content"), 1);
    test:assertEquals(rec1.B.length(), 2);
    test:assertEquals(rec1.B[0].get("#content"), 1.0);
    test:assertEquals(rec1.B[1].get("#content"), 2.0);
}

@Name {
    value: "Data8"
}
type Rec2 record {|
    @Name {
        value: "A"
    }
    record {|
        @Name {
            value: "D"
        }
        record {|
            @Name {
                value: "C"
            }
            record {|
                @Name {
                    value: "D"
                }
                int[] d;
            |}[] c;
        |} d;
    |} a;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord7() returns error? {
    string xmlStr1 = "<Data8><A><D><C><D>1</D><D>2</D></C><C><D>3</D><D>4</D></C></D></A></Data8>";
    Rec2 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.a.d.c.length(), 2);
    test:assertEquals(rec1.a.d.c[0].d.length(), 2);
    test:assertEquals(rec1.a.d.c[0].d[0], 1);
    test:assertEquals(rec1.a.d.c[0].d[1], 2);
    test:assertEquals(rec1.a.d.c[1].d.length(), 2);
    test:assertEquals(rec1.a.d.c[1].d[0], 3);
    test:assertEquals(rec1.a.d.c[1].d[1], 4);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord7() returns error? {
    xml xmlVal1 = xml `<Data8><A><D><C><D>1</D><D>2</D></C><C><D>3</D><D>4</D></C></D></A></Data8>`;
    Rec2 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.a.d.c.length(), 2);
    test:assertEquals(rec1.a.d.c[0].d.length(), 2);
    test:assertEquals(rec1.a.d.c[0].d[0], 1);
    test:assertEquals(rec1.a.d.c[0].d[1], 2);
    test:assertEquals(rec1.a.d.c[1].d.length(), 2);
    test:assertEquals(rec1.a.d.c[1].d[0], 3);
    test:assertEquals(rec1.a.d.c[1].d[1], 4);
}

// Rest field tests

type RecRest1 record {|
    record {
        int C;
    } A;
    int B;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord21() returns error? {
    string xmlStr1 = "<RecRest1><A><C>1</C><D>3</D></A><B>2</B></RecRest1>";
    RecRest1 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.C, 1);
    test:assertEquals(rec1.A.get("D"), 3);
    test:assertEquals(rec1.B, 2);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord21() returns error? {
    xml xmlVal1 = xml `<RecRest1><A><C>1</C><D>3</D></A><B>2</B></RecRest1>`;
    RecRest1 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.C, 1);
    test:assertEquals(rec1.A.get("D"), 3);
    test:assertEquals(rec1.B, 2);
}

type RecRest2 record {|
    record {
        int C;
        record {
            int E;
        } D;
    } A;
    int B;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord22() returns error? {
    string xmlStr1 = "<RecRest2><A><C>1</C><D><E>3</E><F>4</F></D></A><B>2</B></RecRest2>";
    RecRest2 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.C, 1);
    test:assertEquals(rec1.A.D.E, 3);
    test:assertEquals(rec1.A.D.get("F"), 4);
    test:assertEquals(rec1.B, 2);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord22() returns error? {
    xml xmlVal1 = xml `<RecRest2><A><C>1</C><D><E>3</E><F>4</F></D></A><B>2</B></RecRest2>`;
    RecRest2 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.C, 1);
    test:assertEquals(rec1.A.D.E, 3);
    test:assertEquals(rec1.A.D.get("F"), 4);
    test:assertEquals(rec1.B, 2);
}

type RecRest3 record {|
    record {
        int C;
        record {
            int E;
        }[] D;
    } A;
    int B;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord23() returns error? {
    string xmlStr1 = "<RecRest3><A><C>1</C><D><E>3</E><F>4</F></D><D><E>5</E><F>6</F></D></A><B>2</B></RecRest3>";
    RecRest3 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.C, 1);
    test:assertEquals(rec1.A.D.length(), 2);
    test:assertEquals(rec1.A.D[0].E, 3);
    test:assertEquals(rec1.A.D[0].get("F"), 4);
    test:assertEquals(rec1.A.D[1].E, 5);
    test:assertEquals(rec1.A.D[1].get("F"), 6);
    test:assertEquals(rec1.B, 2);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord23() returns error? {
    xml xmlVal1 = xml `<RecRest3><A><C>1</C><D><E>3</E><F>4</F></D><D><E>5</E><F>6</F></D></A><B>2</B></RecRest3>`;
    RecRest3 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.C, 1);
    test:assertEquals(rec1.A.D.length(), 2);
    test:assertEquals(rec1.A.D[0].E, 3);
    test:assertEquals(rec1.A.D[0].get("F"), 4);
    test:assertEquals(rec1.A.D[1].E, 5);
    test:assertEquals(rec1.A.D[1].get("F"), 6);
    test:assertEquals(rec1.B, 2);
}

type RecRest4 record {|
    record {
        record {|
            string name;
        |} D;
    }[]...;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord24() returns error? {
    string xmlStr1 = "<RecRest4><b><D><name>James</name></D></b><b><D><name>Clark</name></D></b></RecRest4>";
    RecRest4 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.get("b")[0].D.name, "James");
    test:assertEquals(rec1.get("b")[1].D.name, "Clark");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord24() returns error? {
    xml xmlVal1 = xml `<RecRest4><b><D><name>James</name></D></b><b><D><name>Clark</name></D></b></RecRest4>`;
    RecRest4 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.get("b")[0].D.name, "James");
    test:assertEquals(rec1.get("b")[1].D.name, "Clark");
}

type RecRest5 record {|
    record {
        record {|
            string name;
        |} D;
    }...;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord25() returns error? {
    string xmlStr1 = "<Data><b><D><name>James</name></D></b><c><D><name>Clark</name></D></c></Data>";
    RecRest5 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.get("b").D.name, "James");
    test:assertEquals(rec1.get("c").D.name, "Clark");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord25() returns error? {
    xml xmlVal1 = xml `<Data><b><D><name>James</name></D></b><c><D><name>Clark</name></D></c></Data>`;
    RecRest5 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.get("b").D.name, "James");
    test:assertEquals(rec1.get("c").D.name, "Clark");
}

type RecRest6 record {|
    int A;
    int[]...;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord26() returns error? {
    string xmlStr = string `
    <Data>
        <A>1</A>
        <B>2</B>
        <B>3</B>
    </Data>`;
    RecRest6 rec = check parseString(xmlStr);
    test:assertEquals(rec.A, 1);
    test:assertEquals((<int[]>rec.get("B"))[0], 2);
    test:assertEquals((<int[]>rec.get("B"))[1], 3);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord26() returns error? {
    xml xmlVal = xml `<Data>
        <A>1</A>
        <B>2</B>
        <B>3</B>
    </Data>`;
    RecRest6 rec = check parseAsType(xmlVal);
    test:assertEquals(rec.A, 1);
    test:assertEquals((<int[]>rec.get("B"))[0], 2);
    test:assertEquals((<int[]>rec.get("B"))[1], 3);
}

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord27() returns error? {
    string xmlStr = string `
    <Data>
        <A><B>1</B></A>
        <A><B>2</B></A>
        <A><B>3</B></A>
        <D>4</D>
    </Data>`;

    record {|
        string D;
        record {|
            int B;
        |}[]...;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.D, "4");
    test:assertEquals((<record {|int B;|}[]>rec.get("A"))[0].B, 1);
    test:assertEquals((<record {|int B;|}[]>rec.get("A"))[1].B, 2);
    test:assertEquals((<record {|int B;|}[]>rec.get("A"))[2].B, 3);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord27() returns error? {
    xml xmlVal = xml `
    <Data>
        <A><B>1</B></A>
        <A><B>2</B></A>
        <A><B>3</B></A>
        <D>4</D>
    </Data>`;

    record {|
        string D;
        record {|
            int B;
        |}[]...;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.D, "4");
    test:assertEquals((<record {|int B;|}[]>rec.get("A"))[0].B, 1);
    test:assertEquals((<record {|int B;|}[]>rec.get("A"))[1].B, 2);
    test:assertEquals((<record {|int B;|}[]>rec.get("A"))[2].B, 3);
}

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord28() returns error? {
    string xmlStr = string `
        <Data>
            <A><B>1</B></A>
            <A><B>2</B></A>
            <A><B>3</B></A>
            <C><B>4</B></C>
            <D>4</D>
        </Data>
    `;

    record {} rec = check parseString(xmlStr);
    test:assertEquals(rec.get("D"), 4);
    test:assertEquals(rec.get("A"), [{B: 1}, {B: 2}, {B: 3}]);
    test:assertEquals(rec.get("C"), {B: 4});
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord28() returns error? {
    xml xmlVal = xml `
        <Data>
            <A><B>1</B></A>
            <A><B>2</B></A>
            <A><B>3</B></A>
            <C><B>4</B></C>
            <D>4</D>
        </Data>
    `;

    record {} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.get("D"), 4);
    test:assertEquals(rec.get("A"), [{B: 1}, {B: 2}, {B: 3}]);
    test:assertEquals(rec.get("C"), {B: 4});
}

// test namespace and attributes annotations

type RecAtt1 record {|
    record {|
        @Attribute
        int c;
        int \#content;
    |}[] A;
    int B;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord30() returns error? {
    string xmlStr1 = "<RecAtt1><A c=\"3\">1</A><A c=\"5\">6</A><B>2</B></RecAtt1>";
    RecAtt1 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A.length(), 2);
    test:assertEquals(rec1.A[0].c, 3);
    test:assertEquals(rec1.A[0].get("#content"), 1);
    test:assertEquals(rec1.A[1].c, 5);
    test:assertEquals(rec1.A[1].get("#content"), 6);
    test:assertEquals(rec1.B, 2);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord30() returns error? {
    xml xmlVal1 = xml `<RecAtt1><A c="3">1</A><A c="5">6</A><B>2</B></RecAtt1>`;
    RecAtt1 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A.length(), 2);
    test:assertEquals(rec1.A[0].c, 3);
    test:assertEquals(rec1.A[0].get("#content"), 1);
    test:assertEquals(rec1.A[1].c, 5);
    test:assertEquals(rec1.A[1].get("#content"), 6);
    test:assertEquals(rec1.B, 2);
}

type RecAtt2 record {|
    RecNs1[] A;
|};

type RecNs1 record {|
    @Namespace {
        prefix: "ns1",
        uri: "http://example.com"
    }
    @Attribute
    string data;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord31() returns error? {
    string xmlStr = string `
    <RecAtt2>
        <A xmlns:ns1="http://example.com" ns1:data="James"></A>
        <A xmlns:ns1="http://example.com" ns1:data="Gunn"></A>
    </RecAtt2>`;
    RecAtt2 rec = check parseString(xmlStr);
    test:assertEquals(rec.A.length(), 2);
    test:assertEquals(rec.A[0].get("data"), "James");
    test:assertEquals(rec.A[1].get("data"), "Gunn");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord31() returns error? {
    xml xmlVal = xml `
    <RecAtt2>
        <A xmlns:ns1="http://example.com" ns1:data="James"></A>
        <A xmlns:ns1="http://example.com" ns1:data="Gunn"></A>
    </RecAtt2>`;
    RecAtt2 rec = check parseAsType(xmlVal);
    test:assertEquals(rec.A.length(), 2);
    test:assertEquals(rec.A[0].get("data"), "James");
    test:assertEquals(rec.A[1].get("data"), "Gunn");
}

type Rec record {|
    string element1;
    string element2;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord32() returns error? {
    string xmlStr1 = string `
    <root xmlns:example="http://www.example.com">
        <example:element1>Value 1</example:element1>
        <element2>Value 2</element2>
    </root>`;

    Rec rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.element1, "Value 1");
    test:assertEquals(rec1.element2, "Value 2");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord32() returns error? {
    xml xmlVal1 = xml `
    <root xmlns:example="http://www.example.com">
        <example:element1>Value 1</example:element1>
        <element2>Value 2</element2>
    </root>`;

    Rec rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.element1, "Value 1");
    test:assertEquals(rec1.element2, "Value 2");
}

@Namespace {
    prefix: "x",
    uri: "example.com"
}
@Name {
    value: "foo"
}
type NSRec1 record {|
    string \#content;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord33() returns error? {
    string xmlStr1 = string `<x:foo xmlns:x="example.com">1</x:foo>`;
    NSRec1 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.get("#content"), "1");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord33() returns error? {
    xml xmlVal1 = xml `<x:foo xmlns:x="example.com">1</x:foo>`;
    NSRec1 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.get("#content"), "1");
}

@Namespace {
    prefix: "x",
    uri: "example.com"
}
type NSRec2 record {|
    @Namespace {
        prefix: "x",
        uri: "example.com"
    }
    string bar;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord34() returns error? {
    string xmlStr1 = string `<x:foo xmlns:x="example.com"><x:bar>1</x:bar></x:foo>`;
    NSRec2 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.bar, "1");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord34() returns error? {
    xml xmlVal1 = xml `<x:foo xmlns:x="example.com"><x:bar>1</x:bar></x:foo>`;
    NSRec2 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.bar, "1");
}

@Namespace {
    prefix: "x",
    uri: "example.com"
}
@Name {
    value: "foo"
}
type NSRec3 record {|
    @Namespace {
        prefix: "x",
        uri: "example.com"
    }
    record {|
        @Namespace {
            uri: "example2.com"
        }
        string baz;
    |} bar;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord35() returns error? {
    string xmlStr1 = string `<x:foo xmlns:x="example.com" xmlns="example2.com"><x:bar><baz>2</baz></x:bar></x:foo>`;
    NSRec3 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.bar.baz, "2");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord35() returns error? {
    xml xmlVal1 = xml `<x:foo xmlns:x="example.com" xmlns="example2.com"><x:bar><baz>2</baz></x:bar></x:foo>`;
    NSRec3 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.bar.baz, "2");
}

@Namespace {
    uri: "example.com"
}
type NSRec4 record {|
    @Namespace {
        uri: "example.com"
    }
    string bar;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord36() returns error? {
    string xmlStr1 = string `<foo xmlns="example.com"><bar>1</bar></foo>`;
    NSRec4 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.bar, "1");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord36() returns error? {
    xml xmlVal = xml `<foo xmlns="example.com"><bar>1</bar></foo>`;
    NSRec4 rec1 = check parseAsType(xmlVal);
    test:assertEquals(rec1.bar, "1");
}

@Namespace {
    prefix: "ns1",
    uri: "http://example.com"
}
type NSRec5 record {|
    @Namespace {
        uri: "http://ballerina.io"
    }
    record {|
        @Attribute
        string value;
        string \#content;
    |} name;
    @Namespace {
        prefix: "ns2",
        uri: "http://workspace.com"
    }
    int age;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord37() returns error? {
    string xmlStr = string `<ns1:Person xmlns:ns1="http://example.com">
        <name xmlns="http://ballerina.io" value="karen">kumar</name>
        <ns2:age xmlns:ns2="http://workspace.com">18</ns2:age>
    </ns1:Person>`;

    NSRec5 rec = check parseString(xmlStr);
    test:assertEquals(rec.name.value, "karen");
    test:assertEquals(rec.name.get("#content"), "kumar");
    test:assertEquals(rec.age, 18);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord37() returns error? {
    xml xmlVal = xml `<ns1:Person xmlns:ns1="http://example.com">
        <name xmlns="http://ballerina.io" value="karen">kumar</name>
        <ns2:age xmlns:ns2="http://workspace.com">18</ns2:age>
    </ns1:Person>`;

    NSRec5 rec = check parseAsType(xmlVal);
    test:assertEquals(rec.name.value, "karen");
    test:assertEquals(rec.name.get("#content"), "kumar");
    test:assertEquals(rec.age, 18);
}

type RecAtt3 record {|
    string A;
|};

type RecAtt4 record {|
    record {} A;
|};

type RecAtt5 record {|
    @Attribute
    string A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord38() returns error? {
    string xmlStr = string `<Data101 A="name"><A>1</A></Data101>`;
    RecAtt3 rec = check parseString(xmlStr);
    test:assertEquals(rec.A, "1");

    RecAtt4 rec2 = check parseString(xmlStr);
    test:assertEquals(rec2.A.get("#content"), 1);

    RecAtt5 rec3 = check parseString(xmlStr);
    test:assertEquals(rec3.A, "name");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord38() returns error? {
    xml xmlVal = xml `<Data101 A="name"><A>1</A></Data101>`;
    RecAtt3 rec = check parseAsType(xmlVal);
    test:assertEquals(rec.A, "1");

    RecAtt4 rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.A.get("#content"), 1);

    RecAtt5 rec3 = check parseAsType(xmlVal);
    test:assertEquals(rec3.A, "name");
}

type RecAtt6 record {|
    record {|
        @Attribute
        string data;
        string \#content;
    |}...;
|};

type RecAtt7 record {|
    record {|
        record {|
            @Attribute
            string data;
            string \#content;
        |} person;
    |}...;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord39() returns error? {
    string xmlStr = string `<Data>
        <A data="dataValue1">1</A>
        <B data="dataValue2">2</B>
    </Data>`;

    RecAtt6 rec = check parseString(xmlStr);
    test:assertEquals(rec.get("A").data, "dataValue1");
    test:assertEquals(rec.get("A").get("#content"), "1");

    string xmlStr2 = string `<Data>
        <A><person data="James">1</person></A>
        <B><person data="Gunn">2</person></B>
        </Data>`;
    RecAtt7 rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.get("A").person.data, "James");
    test:assertEquals(rec2.get("A").person.get("#content"), "1");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord39() returns error? {
    xml xmlVal1 = xml `<Data>
        <A data="dataValue1">1</A>
        <B data="dataValue2">2</B>
    </Data>`;

    RecAtt6 rec = check parseAsType(xmlVal1);
    test:assertEquals(rec.get("A").data, "dataValue1");
    test:assertEquals(rec.get("A").get("#content"), "1");

    xml xmlVal2 = xml `<Data>
        <A><person data="James">1</person></A>
        <B><person data="Gunn">2</person></B>
        </Data>`;
    RecAtt7 rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.get("A").person.data, "James");
    test:assertEquals(rec2.get("A").person.get("#content"), "1");
}

// Test projection with fixed array size.

type DataProj record {|
    record {|
        int B;
    |}[1] A;
|};

type DataProj2 record {|
    int[] A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord40() returns error? {
    string xmlStr = string `<DataProj>
        <A><B>1</B></A>
        <A><B>2</B></A>
        <A><B>3</B></A>
    </DataProj>`;
    DataProj rec = check parseString(xmlStr);
    test:assertEquals(rec.A.length(), 1);
    test:assertEquals(rec.A[0].B, 1);

    string xmlStr2 = string `<DataProj2>
        <A>1</A>
        <A>2</A>
        <A>3</A>
    </DataProj2>`;

    DataProj2 rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.A.length(), 3);
    test:assertEquals(rec2.A[0], 1);
    test:assertEquals(rec2.A[1], 2);
    test:assertEquals(rec2.A[2], 3);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord40() returns error? {
    xml xmlVal = xml `<DataProj>
        <A><B>1</B></A>
        <A><B>2</B></A>
        <A><B>3</B></A>
    </DataProj>`;
    DataProj rec = check parseAsType(xmlVal);
    test:assertEquals(rec.A.length(), 1);
    test:assertEquals(rec.A[0].B, 1);

    xml xmlVal2 = xml `<DataProj2>
        <A>1</A>
        <A>2</A>
        <A>3</A>
    </DataProj2>`;

    DataProj2 rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.A.length(), 3);
    test:assertEquals(rec2.A[0], 1);
    test:assertEquals(rec2.A[1], 2);
    test:assertEquals(rec2.A[2], 3);
}

type DataProj3 record {|
    record {|
        int B;
    |}[2] A;
|};

type DataProj4 record {|
    int[2] A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord41() returns error? {
    string xmlStr = string `<DataProj3>
        <A><B>1</B></A>
        <A><B>2</B></A>
        <A><B>3</B></A>
    </DataProj3>`;
    DataProj3 rec = check parseString(xmlStr);
    test:assertEquals(rec.A.length(), 2);
    test:assertEquals(rec.A[0].B, 1);
    test:assertEquals(rec.A[1].B, 2);

    string xmlStr2 = string `<DataProj4>
        <A>1</A>
        <A>2</A>
        <A>3</A>
    </DataProj4>`;

    DataProj4 rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.A.length(), 2);
    test:assertEquals(rec2.A[0], 1);
    test:assertEquals(rec2.A[1], 2);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord41() returns error? {
    xml xmlVal = xml `<DataProj3>
        <A><B>1</B></A>
        <A><B>2</B></A>
        <A><B>3</B></A>
    </DataProj3>`;
    DataProj3 rec = check parseAsType(xmlVal);
    test:assertEquals(rec.A.length(), 2);
    test:assertEquals(rec.A[0].B, 1);
    test:assertEquals(rec.A[1].B, 2);

    xml xmlVal2 = xml `<DataProj4>
        <A>1</A>
        <A>2</A>
        <A>3</A>
    </DataProj4>`;

    DataProj4 rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.A.length(), 2);
    test:assertEquals(rec2.A[0], 1);
    test:assertEquals(rec2.A[1], 2);
}

@Namespace {
    uri: "http://www.example.com/invoice",
    prefix: "inv"
}
@Name {
    value: "invoice"
}
type DataProj5 record {|
    DataProjField customers;
|};

type DataProjField record {|
    @Name {
        value: "customer"
    }
    string[2] cust;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord42() returns error? {
    string xmlStr = string `<inv:invoice xmlns:inv="http://www.example.com/invoice">
        <customers>
            <customer>KLLBCQVN0Y</customer>
            <customer>T8VQU3X0QH</customer>
            <customer>DAWQU3X0QH</customer>
        </customers>
    </inv:invoice>`;

    DataProj5 invoice = check parseString(xmlStr);
    test:assertEquals(invoice.customers.cust.length(), 2);
    test:assertEquals(invoice.customers.cust[0], "KLLBCQVN0Y");
    test:assertEquals(invoice.customers.cust[1], "T8VQU3X0QH");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord42() returns error? {
    xml xmlVal = xml `<inv:invoice xmlns:inv="http://www.example.com/invoice">
        <customers>
            <customer>KLLBCQVN0Y</customer>
            <customer>T8VQU3X0QH</customer>
            <customer>DAWQU3X0QH</customer>
        </customers>
    </inv:invoice>`;

    DataProj5 invoice = check parseAsType(xmlVal);
    test:assertEquals(invoice.customers.cust.length(), 2);
    test:assertEquals(invoice.customers.cust[0], "KLLBCQVN0Y");
    test:assertEquals(invoice.customers.cust[1], "T8VQU3X0QH");
}

// Test xml with comment, processing instructions.

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecord51() returns error? {
    string xmlStr = string `<?xml version="1.0" encoding="UTF-8"?>
        <!-- This is a sample XML document with comments and a processing instruction. -->

        <root>
            <!-- This is a comment within the root element. -->
            
            <?target instruction?>
            
            <element1>
                <!-- Comment for element1 -->
                <subelement>Value 1</subelement>
            </element1>
            
            <element2>
                <!-- Comment for element2 -->
                <subelement>Value 2</subelement>
            </element2>
        </root>`;
    record {|
        record {} element1;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.element1.get("subelement"), "Value 1");

    record {|
        map<string> element1;
        map<string> element2;
    |} rec2 = check parseString(xmlStr);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.element1.get("subelement"), "Value 1");
    test:assertEquals(rec2.element2.get("subelement"), "Value 2");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecord51() returns error? {
    xml xmlVal = xml `<?xml version="1.0" encoding="UTF-8"?>
        <!-- This is a sample XML document with comments and a processing instruction. -->

        <root>
            <!-- This is a comment within the root element. -->
            
            <?target instruction?>
            
            <element1>
                <!-- Comment for element1 -->
                <subelement>Value 1</subelement>
            </element1>
            
            <element2>
                <!-- Comment for element2 -->
                <subelement>Value 2</subelement>
            </element2>
        </root>`;
    record {|
        record {} element1;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.element1.get("subelement"), "Value 1");

    record {|
        map<string> element1;
        map<string> element2;
    |} rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.element1.get("subelement"), "Value 1");
    test:assertEquals(rec2.element2.get("subelement"), "Value 2");
}

@test:Config {
    groups: ["fromXmlString"]
}
function testOptionalFieldInXmlConversion() returns error? {
    string xmlStr = string `<Data><A></A><B>2</B></Data>`;
    record {|
        string A?;
        string B;
    |} rec1 = check parseString(xmlStr);
    test:assertEquals(rec1.length(), 1);
    test:assertEquals(rec1.B, "2");

    xml xmlVal = xml `<Data><A></A><B>2</B></Data>`;
    record {|
        string A?;
        string B;
    |} rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.B, "2");
}

type RecNs2 record {|
    @Namespace {
        prefix: "x",
        uri: "example.com"
    }
    string bar;
    @Name {
        value: "bar"
    }
    @Namespace {
        prefix: "y",
        uri: "example2.com"
    }
    string baz;
|};

@test:Config
function testSameElementWithDifferentNameSpace() returns error? {
    string xmlStr = string `<x:foo xmlns:x="example.com" xmlns:y="example2.com" ><x:bar>1</x:bar><y:bar>2</y:bar></x:foo>`;
    RecNs2 rec = check parseString(xmlStr);
    test:assertEquals(rec.bar, "1");
    test:assertEquals(rec.baz, "2");

    xml xmlVal = xml `<x:foo xmlns:x="example.com" xmlns:y="example2.com"><x:bar>1</x:bar><y:bar>2</y:bar></x:foo>`;
    RecNs2 rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.bar, "1");
    test:assertEquals(rec2.baz, "2");
}

@test:Config
function testSameAttributeWithDifferentNameSpace() returns error? {
    string xmlStr = string `<x:foo xmlns:x="example.com" xmlns:y="example2.com" x:bar="1" y:bar="2"></x:foo>`;
    RecNs2 rec = check parseString(xmlStr);
    test:assertEquals(rec.bar, "1");
    test:assertEquals(rec.baz, "2");

    xml xmlVal = xml `<x:foo xmlns:x="example.com" xmlns:y="example2.com" x:bar="1" y:bar="2"></x:foo>`;
    RecNs2 rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.bar, "1");
    test:assertEquals(rec2.baz, "2");
}

@test:Config
function testXmlWithAttributesAgainstOpenRecord1() returns error? {
    string xmlStr = string `<root>
        <element1 attribute1="value1" attribute2="value2">
            <subelement1 attribute3="value3" />
            <subelement2 attribute4="value4" attribute5="value5" />
        </element1>
        <element2 attribute6="value6">
            <subelement3 attribute7="value7" />
        </element2>
    </root>`;

    record {} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("element1"), {
        "attribute1": "value1",
        "attribute2": "value2",
        "subelement1": {"attribute3": "value3"},
        "subelement2": {"attribute4": "value4", "attribute5": "value5"}
    }
    );
    test:assertEquals(rec.get("element2"), {
        "attribute6": "value6",
        "subelement3": {"attribute7": "value7"}
    }
    );

    xml xmlVal = xml `<root>
        <element1 attribute1="value1" attribute2="value2">
            <subelement1 attribute3="value3"/>
            <subelement2 attribute4="value4" attribute5="value5"/>
        </element1>
        <element2 attribute6="value6">
            <subelement3 attribute7="value7"/>
        </element2>
    </root>`;

    record {} rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.get("element1"), {
        "attribute1": "value1",
        "attribute2": "value2",
        "subelement1": {"attribute3": "value3"},
        "subelement2": {"attribute4": "value4", "attribute5": "value5"}
    }
    );
    test:assertEquals(rec2.get("element2"), {
        "attribute6": "value6",
        "subelement3": {"attribute7": "value7"}
    }
    );
}

@test:Config
function testXmlWithAttributesAgainstOpenRecord2() returns error? {
    string xmlStr2 = string `
        <bookstore>
            <book ISBN="978-0-12-345678-9">
                <title>The Example Book</title>
                <author>
                    <name>John Doe</name>
                    <affiliation>Example Publications</affiliation>
                </author>
                <price currency="USD">19.99</price>
            </book>
            <book ISBN="978-0-98-765432-1">
                <title>Another Book</title>
                <author>
                    <name>Jane Smith</name>
                    <affiliation>Book World</affiliation>
                </author>
                <price currency="EUR">29.95</price>
            </book>
        </bookstore>
    `;

    record {} rec3 = check parseString(xmlStr2);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.get("book"), [
        {
            "ISBN": "978-0-12-345678-9",
            "title": "The Example Book",
            "author": {
                "name": "John Doe",
                "affiliation": "Example Publications"
            },
            "price": {
                "currency": "USD",
                "#content": 19.99
            }
        },
        {
            "ISBN": "978-0-98-765432-1",
            "title": "Another Book",
            "author": {
                "name": "Jane Smith",
                "affiliation": "Book World"
            },
            "price": {
                "currency": "EUR",
                "#content": 29.95
            }
        }
    ]);

    xml xmlVal2 = xml `
        <bookstore>
            <book ISBN="978-0-12-345678-9">
                <title>The Example Book</title>
                <author>
                    <name>John Doe</name>
                    <affiliation>Example Publications</affiliation>
                </author>
                <price currency="USD">19.99</price>
            </book>
            <book ISBN="978-0-98-765432-1">
                <title>Another Book</title>
                <author>
                    <name>Jane Smith</name>
                    <affiliation>Book World</affiliation>
                </author>
                <price currency="EUR">29.95</price>
            </book>
        </bookstore>
    `;
    record {} rec4 = check parseAsType(xmlVal2);
    test:assertEquals(rec4.length(), 1);
    test:assertEquals(rec4.get("book"), [
        {
            "ISBN": "978-0-12-345678-9",
            "title": "The Example Book",
            "author": {
                "name": "John Doe",
                "affiliation": "Example Publications"
            },
            "price": {
                "currency": "USD",
                "#content": 19.99
            }
        },
        {
            "ISBN": "978-0-98-765432-1",
            "title": "Another Book",
            "author": {
                "name": "Jane Smith",
                "affiliation": "Book World"
            },
            "price": {
                "currency": "EUR",
                "#content": 29.95
            }
        }
    ]);
}

@test:Config
function testXmlWithAttributesAgainstOpenRecord3() returns error? {
    string xmlStr3 = string `<Data>
                                <A><B value="name">1</B></A>
                                <A><B value="name">2</B></A>
                                <A><B value="name">3</B></A>
                            </Data>`;
    record {} rec5 = check parseString(xmlStr3);
    test:assertEquals(rec5.length(), 1);
    test:assertEquals(rec5.get("A"), [
        {"B": {"value": "name", "#content": 1}},
        {"B": {"value": "name", "#content": 2}},
        {"B": {"value": "name", "#content": 3}}
    ]);

    xml xmlVal3 = xml `<Data>
                            <A><B value="name">1</B></A>
                            <A><B value="name">2</B></A>
                            <A><B value="name">3</B></A>
                        </Data>`;
    record {} rec6 = check parseAsType(xmlVal3);
    test:assertEquals(rec6.length(), 1);
    test:assertEquals(rec6.get("A"), [
        {"B": {"value": "name", "#content": 1}},
        {"B": {"value": "name", "#content": 2}},
        {"B": {"value": "name", "#content": 3}}
    ]);
}

@test:Config
function testCommentMiddleInContent1() returns error? {
    string xmlStr = string `<Data>
                                <A>John<!-- firstname --> Doe<!-- lastname --></A>
                            </Data>`;
    record {} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.get("A"), "John Doe");

    record {|
        string A;
    |} rec2 = check parseString(xmlStr);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.A, "John Doe");
}

@test:Config
function testCommentMiddleInContent2() returns error? {
    xml xmlVal = xml `<Data>
                        <A>John<!-- firstname --> Doe<!-- lastname --></A>
                    </Data>`;
    record {} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.get("A"), "John Doe");

    record {|
        string A;
    |} rec2 = check parseAsType(xmlVal);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.A, "John Doe");
}

@test:Config
function testAnydataAsRestFieldWithParseString() returns error? {
    string xmlStr = string `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        anydata...;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), [1, 2]);
    test:assertEquals(rec.get("B"), ["Code_1", "Code_2"]);

    string xmlStr2 = string `<Data>
                    <A><C>1</C></A>
                    <A><C>2</C></A>
                    <B><C>Code_1</C></B>
                    <B><C>Code_2</C></B>
                </Data>`;
    record {|
        anydata...;
    |} rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.get("A"), [{C: 1}, {C: 2}]);
    test:assertEquals(rec2.get("B"), [{C: "Code_1"}, {C: "Code_2"}]);

    string xmlStr3 = string `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        anydata...;
    |} rec3 = check parseString(xmlStr3);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.get("Employee"), [
        {
            "Name": "John Doe",
            "age": 30
        },
        {
            "Name": "Kanth Kevin",
            "age": 26
        }
    ]);
}

@test:Config
function testAnydataAsRestFieldWithParseAsType() returns error? {
    xml xmlVal = xml `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        anydata...;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), [1, 2]);
    test:assertEquals(rec.get("B"), ["Code_1", "Code_2"]);

    xml xmlVal2 = xml `<Data>
                    <A><C>1</C></A>
                    <A><C>2</C></A>
                    <B><C>Code_1</C></B>
                    <B><C>Code_2</C></B>
                </Data>`;
    record {|
        anydata...;
    |} rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.get("A"), [{C: 1}, {C: 2}]);
    test:assertEquals(rec2.get("B"), [{C: "Code_1"}, {C: "Code_2"}]);

    xml xmlVal3 = xml `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        anydata...;
    |} rec3 = check parseAsType(xmlVal3);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.get("Employee"), [
        {
            "Name": "John Doe",
            "age": 30
        },
        {
            "Name": "Kanth Kevin",
            "age": 26
        }
    ]);
}

@test:Config
function testAnydataAsFieldTypeWiThParseString() returns error? {
    string xmlStr = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, {
        "Name": "John Doe",
        "Age": 30
    });

    string xmlStr2 = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.Employee,
        [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]
    );

    string xmlStr3 = string `<Company>
                        <name>WSO2</name>
                    </Company>`;
    record {
        anydata name;
    } rec3 = check parseString(xmlStr3);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.name, "WSO2");

    string xmlStr4 = string `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec4 = check parseString(xmlStr4);
    test:assertEquals(rec4.length(), 1);
    test:assertEquals(rec4.Employee,
        [
        {
            "Name": "John Doe",
            "age": 30
        },
        {
            "Name": "Kanth Kevin",
            "age": 26
        }
    ]
    );

    string xmlStr5 = string `<Company>
                        <name>WSO2</name>
                        <founder age="55">Sanjeeva</founder>
                        <founder age="58">Paul Fremantle</founder>
                    </Company>`;
    record {
        anydata name;
        anydata founder;
    } rec5 = check parseString(xmlStr5);
    test:assertEquals(rec5.length(), 2);
    test:assertEquals(rec5.name, "WSO2");
    test:assertEquals(rec5.founder, [
        {
            "age": 55,
            "#content": "Sanjeeva"
        },
        {
            "age": 58,
            "#content": "Paul Fremantle"
        }
    ]);

    string xmlStr6 = string `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec6 = check parseString(xmlStr6);
    test:assertEquals(rec6.length(), 1);
    test:assertEquals(rec6, {
        Employee: [
            {
                "Name": "John Doe",
                "age": 30
            },
            {
                "Name": "Kanth Kevin",
                "age": 26
            }
        ]
    });

    string xmlStr7 = string `<Data>
                    <A>1</A>
                    <B>Code_1</B>
                </Data>`;
    record {|
        anydata A;
        anydata B;
    |} rec7 = check parseString(xmlStr7);
    test:assertEquals(rec7.length(), 2);
    test:assertEquals(rec7.A, 1);
    test:assertEquals(rec7.B, "Code_1");

    string xmlStr8 = string `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        anydata A;
        anydata B;
    |} rec8 = check parseString(xmlStr8);
    test:assertEquals(rec8.length(), 2);
    test:assertEquals(rec8.A, [1, 2]);
    test:assertEquals(rec8.B, ["Code_1", "Code_2"]);
}

@test:Config
function testAnydataAsFieldTypeWiThParseAsType() returns error? {
    xml xmlVal = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, {
        "Name": "John Doe",
        "Age": 30
    });

    xml xmlVal2 = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.Employee,
        [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]
    );

    xml xmlVal3 = xml `<Company>
                        <name>WSO2</name>
                    </Company>`;
    record {
        anydata name;
    } rec3 = check parseAsType(xmlVal3);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.name, "WSO2");

    xml xmlVal4 = xml `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec4 = check parseAsType(xmlVal4);
    test:assertEquals(rec4.length(), 1);
    test:assertEquals(rec4.Employee,
        [
        {
            "Name": "John Doe",
            "age": 30
        },
        {
            "Name": "Kanth Kevin",
            "age": 26
        }
    ]
    );

    xml xmlVal5 = xml `<Company>
                        <name>WSO2</name>
                        <founder age="55">Sanjeeva</founder>
                        <founder age="58">Paul Fremantle</founder>
                    </Company>`;
    record {
        anydata name;
        anydata founder;
    } rec5 = check parseAsType(xmlVal5);
    test:assertEquals(rec5.length(), 2);
    test:assertEquals(rec5.name, "WSO2");
    test:assertEquals(rec5.founder, [
        {
            "age": 55,
            "#content": "Sanjeeva"
        },
        {
            "age": 58,
            "#content": "Paul Fremantle"
        }
    ]);

    xml xmlVal6 = xml `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        anydata Employee;
    |} rec6 = check parseAsType(xmlVal6);
    test:assertEquals(rec6.length(), 1);
    test:assertEquals(rec6, {
        Employee: [
            {
                "Name": "John Doe",
                "age": 30
            },
            {
                "Name": "Kanth Kevin",
                "age": 26
            }
        ]
    });

    xml xmlVal7 = xml `<Data>
                    <A>1</A>
                    <B>Code_1</B>
                </Data>`;
    record {|
        anydata A;
        anydata B;
    |} rec7 = check parseAsType(xmlVal7);
    test:assertEquals(rec7.length(), 2);
    test:assertEquals(rec7.A, 1);
    test:assertEquals(rec7.B, "Code_1");

    xml xmlVal8 = xml `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        anydata A;
        anydata B;
    |} rec8 = check parseAsType(xmlVal8);
    test:assertEquals(rec8.length(), 2);
    test:assertEquals(rec8.A, [1, 2]);
    test:assertEquals(rec8.B, ["Code_1", "Code_2"]);
}

@test:Config
function testJsonAsFieldTypeWiThParseString() returns error? {
    string xmlStr = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, {
        "Name": "John Doe",
        "Age": 30
    });

    string xmlStr2 = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.Employee,
        [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]
    );

    string xmlStr3 = string `<Company>
                        <name>WSO2</name>
                    </Company>`;
    record {
        json name;
    } rec3 = check parseString(xmlStr3);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.name, "WSO2");

    string xmlStr6 = string `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec6 = check parseString(xmlStr6);
    test:assertEquals(rec6.length(), 1);
    test:assertEquals(rec6, {
        Employee: [
            {
                "Name": "John Doe",
                "age": 30
            },
            {
                "Name": "Kanth Kevin",
                "age": 26
            }
        ]
    });

    string xmlStr7 = string `<Data>
                    <A>1</A>
                    <B>Code_1</B>
                </Data>`;
    record {|
        json A;
        json B;
    |} rec7 = check parseString(xmlStr7);
    test:assertEquals(rec7.length(), 2);
    test:assertEquals(rec7.A, 1);
    test:assertEquals(rec7.B, "Code_1");

    string xmlStr8 = string `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        json A;
        json B;
    |} rec8 = check parseString(xmlStr8);
    test:assertEquals(rec8.length(), 2);
    test:assertEquals(rec8.A, [1, 2]);
    test:assertEquals(rec8.B, ["Code_1", "Code_2"]);
}

@test:Config
function testJsonAsFieldTypeWiThParseAsType() returns error? {
    xml xmlVal = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, {
        "Name": "John Doe",
        "Age": 30
    });

    xml xmlVal2 = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.Employee,
        [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]
    );

    xml xmlVal3 = xml `<Company>
                        <name>WSO2</name>
                    </Company>`;
    record {
        json name;
    } rec3 = check parseAsType(xmlVal3);
    test:assertEquals(rec3.length(), 1);
    test:assertEquals(rec3.name, "WSO2");

    xml xmlVal4 = xml `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec4 = check parseAsType(xmlVal4);
    test:assertEquals(rec4.length(), 1);
    test:assertEquals(rec4.Employee,
        [
        {
            "Name": "John Doe",
            "age": 30
        },
        {
            "Name": "Kanth Kevin",
            "age": 26
        }
    ]
    );

    xml xmlVal5 = xml `<Company>
                        <name>WSO2</name>
                        <founder age="55">Sanjeeva</founder>
                        <founder age="58">Paul Fremantle</founder>
                    </Company>`;
    record {
        json name;
        json founder;
    } rec5 = check parseAsType(xmlVal5);
    test:assertEquals(rec5.length(), 2);
    test:assertEquals(rec5.name, "WSO2");
    test:assertEquals(rec5.founder, [
        {
            "age": 55,
            "#content": "Sanjeeva"
        },
        {
            "age": 58,
            "#content": "Paul Fremantle"
        }
    ]);

    xml xmlVal6 = xml `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Kanth Kevin</Name>
                        </Employee>
                    </Company>`;
    record {|
        json Employee;
    |} rec6 = check parseAsType(xmlVal6);
    test:assertEquals(rec6.length(), 1);
    test:assertEquals(rec6, {
        Employee: [
            {
                "Name": "John Doe",
                "age": 30
            },
            {
                "Name": "Kanth Kevin",
                "age": 26
            }
        ]
    });

    xml xmlVal7 = xml `<Data>
                    <A>1</A>
                    <B>Code_1</B>
                </Data>`;
    record {|
        json A;
        json B;
    |} rec7 = check parseAsType(xmlVal7);
    test:assertEquals(rec7.length(), 2);
    test:assertEquals(rec7.A, 1);
    test:assertEquals(rec7.B, "Code_1");

    xml xmlVal8 = xml `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        json A;
        json B;
    |} rec8 = check parseAsType(xmlVal8);
    test:assertEquals(rec8.length(), 2);
    test:assertEquals(rec8.A, [1, 2]);
    test:assertEquals(rec8.B, ["Code_1", "Code_2"]);
}

@test:Config
function testAnydataArrayAsFieldTypeWiThParseString() returns error? {
    string xmlStr = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata[] Employee;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]);

    string xmlStr2 = string `<Company>
                        <name>WSO2</name>
                        <name>Apple</name>
                        <location>Sri Lanka</location>
                        <location>India</location>
                    </Company>`;
    record {
        anydata[] name;
        anydata[] location;
    } rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.name, ["WSO2", "Apple"]);
    test:assertEquals(rec2.location, ["Sri Lanka", "India"]);
}

@test:Config
function testJsonArrayAsFieldTypeWiThParseString() returns error? {
    string xmlStr = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        json[] Employee;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]);

    string xmlStr2 = string `<Org>
                        <name>WSO2</name>
                        <name>Apple</name>
                    </Org>`;
    record {
        json[] name;
    } rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.name, ["WSO2", "Apple"]);
}

@test:Config
function testAnydataArrayAsFieldTypeWiThParseAsType() returns error? {
    xml xmlVal = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata[] Employee;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]);

    xml xmlVal2 = xml `<Company>
                        <name>WSO2</name>
                        <name>Apple</name>
                        <location>Sri Lanka</location>
                        <location>India</location>
                    </Company>`;
    record {
        anydata[] name;
        anydata[] location;
    } rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.name, ["WSO2", "Apple"]);
    test:assertEquals(rec2.location, ["Sri Lanka", "India"]);
}

@test:Config
function testJsonArrayAsFieldTypeWiThParseAsType() returns error? {
    xml xmlVal = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        json[] Employee;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]);

    xml xmlVal2 = xml `<Company>
                        <name>WSO2</name>
                        <name>Apple</name>
                        <location>Sri Lanka</location>
                        <location>India</location>
                    </Company>`;
    record {
        json[] name;
        json[] location;
    } rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.name, ["WSO2", "Apple"]);
    test:assertEquals(rec2.location, ["Sri Lanka", "India"]);
}

@test:Config
function testMapArrayAsFieldTypeWiThParseString() returns error? {
    string xmlStr = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;

    record {|
        map<string>[] Employee;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "Age": "30"
        },
        {
            "Name": "Kanth Kevin",
            "Age": "26"
        }
    ]);
}

@test:Config
function testMapArrayAsFieldTypeWithParseAsType() returns error? {
    xml xmlVal = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        map<string>[] Employee;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "Age": "30"
        },
        {
            "Name": "Kanth Kevin",
            "Age": "26"
        }
    ]);
}

@test:Config
function testAnydataArrayAsRestTypeWithParseString() returns error? {
    string xmlStr = string `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        anydata[]...;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), [1, 2]);

    string xmlStr2 = string `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata[]...;
    |} rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.get("Employee"), [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]);
}

@test:Config
function testAnydataArrayAsRestTypeWithParseAsType() returns error? {
    xml xmlVal = xml `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code_1</B>
                    <B>Code_2</B>
                </Data>`;
    record {|
        anydata[]...;
    |} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), [1, 2]);

    xml xmlVal2 = xml `<Company>
                        <Employee>
                            <Name>John Doe</Name>
                            <Age>30</Age>
                        </Employee>
                        <Employee>
                            <Name>Kanth Kevin</Name>
                            <Age>26</Age>
                        </Employee>
                    </Company>`;
    record {|
        anydata[]...;
    |} rec2 = check parseAsType(xmlVal2);
    test:assertEquals(rec2.length(), 1);
    test:assertEquals(rec2.get("Employee"), [
        {
            "Name": "John Doe",
            "Age": 30
        },
        {
            "Name": "Kanth Kevin",
            "Age": 26
        }
    ]);
}

@test:Config
function testRecordAsRestTypeForParseString() returns error? {
    string xmlStr = string `<Data>
                    <A><C>A_C_1</C></A>
                    <B><C>B_C_1</C></B>
                </Data>`;
    record {|
        record {}...;
    |} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), {C: "A_C_1"});
    test:assertEquals(rec.get("B"), {C: "B_C_1"});
}

@test:Config
function testRecordArrayAsRestTypeForParseString() returns error? {
    string xmlStr1 = string `<Data>
                    <A>
                        <C>A_C_1</C>
                        <D>A_D_1</D>
                    </A>
                    <A>
                        <C>A_C_2</C>
                        <D>A_D_2</D>
                    </A>
                    <B>
                        <C>B_C_1</C>
                        <D>B_D_1</D>
                    </B>
                    <B>
                        <C>B_C_2</C>
                        <D>B_D_2</D>
                    </B>
                </Data>`;
    record {|
        record {
            string C;
        }[]...;
    |} rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.length(), 2);
    test:assertEquals(rec1.get("A"), [{C: "A_C_1", D: "A_D_1"}, {C: "A_C_2", D: "A_D_2"}]);
    test:assertEquals(rec1.get("B"), [{C: "B_C_1", D: "B_D_1"}, {C: "B_C_2", D: "B_D_2"}]);

    string xmlStr2 = string `
    <Data>
        <Depth1>
            <A>1</A>
            <A>2</A>
            <B>2</B>
            <B>5</B>
        </Depth1>
        <value>3</value>
    </Data>
    `;

    record {|
        string value;
        record {|
            int[]...;
        |}...;
    |} rec2 = check parseString(xmlStr2);
    test:assertEquals(rec2.length(), 2);
    test:assertEquals(rec2.get("Depth1"), {A: [1, 2], B: [2, 5]});
    test:assertEquals(rec2.get("value"), "3");

    record {|
        int value;
        record {|
            string[]...;
        |}...;
    |} rec3 = check parseString(xmlStr2);
    test:assertEquals(rec3.length(), 2);
    test:assertEquals(rec3.get("Depth1"), {A: ["1", "2"], B: ["2", "5"]});
    test:assertEquals(rec3.get("value"), 3);
}

@test:Config
function testAddingContentFieldWhenRestTypeAsExpTypeForParseString() returns error? {
    string xmlStr = string `
    <Data>
        <A a="attribute_a">2</A>
        <B><C c="attribute_c">3</C></B>
    </Data>
    `;

    record {} rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), {\#content: 2, a: "attribute_a"});
    test:assertEquals(rec.get("B"), {C: {\#content: 3, c: "attribute_c"}});

    record {|
        string A;
        record {} B;
    |} val2 = check parseString(xmlStr);
    test:assertEquals(val2.length(), 2);
    test:assertEquals(val2.A, "2");
    test:assertEquals(val2.B, {C: {\#content: 3, c: "attribute_c"}});
}

@test:Config
function testAddingContentFieldWhenRestTypeAsExpTypeForParseAsType() returns error? {
    xml xmlVal = xml `
    <Data>
        <A a="attribute_a">2</A>
        <B><C c="attribute_c">3</C></B>
    </Data>
    `;

    record {} rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.get("A"), {\#content: 2, a: "attribute_a"});
    test:assertEquals(rec.get("B"), {C: {\#content: 3, c: "attribute_c"}});

    record {|
        string A;
        record {} B;
    |} val2 = check parseAsType(xmlVal);
    test:assertEquals(val2.length(), 2);
    test:assertEquals(val2.A, "2");
    test:assertEquals(val2.B, {C: {\#content: 3, c: "attribute_c"}});
}

type ProjectionRec1 record {|
    json[1] Employee;
    anydata[2] Departments;
|};

@test:Config
function testProjectionWithJsonArrayOrAnydataArrayForParseString() returns error? {
    string xmlStr = string `<Company>
                        <Employee age="30">
                            <Name>John Doe</Name>
                        </Employee>
                        <Employee age="26">
                            <Name>Walter White</Name>
                        </Employee>
                        <Departments>Engineering</Departments>
                        <Departments>Hr</Departments>
                        <Departments>Customer Success</Departments>
                    </Company>`;
    record {
        json[1] Employee;
        anydata[2] Departments;
    } rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 2);
    test:assertEquals(rec.Employee, [
        {
            "Name": "John Doe",
            "age": 30
        }
    ]);
    test:assertEquals(rec.Departments, ["Engineering", "Hr"]);
}

type ProjectionRec2 record {|
    string A;
|};

@test:Config
function testProjectionWithXmlAttributeForParseString() returns error? {
    string xmlStr = string `<Data>
                    <A a="attribute_a">2</A>
                    <B><C c="attribute_c">3</C></B>
                </Data>`;
    ProjectionRec2 rec = check parseString(xmlStr);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.A, "2");
}

@test:Config
function testProjectionWithXmlAttributeForParseAsType() returns error? {
    xml xmlVal = xml `<Data>
                    <A a="attribute_a">2</A>
                    <B><C c="attribute_c">3</C></B>
                </Data>`;
    ProjectionRec2 rec = check parseAsType(xmlVal);
    test:assertEquals(rec.length(), 1);
    test:assertEquals(rec.A, "2");
}

type DataN3 record {|
    int[4] A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordWithArrayAsFieldType() returns error? {
    string xmlStr1 = "<Data><A>1</A><A>2</A><A>3</A></Data>";
    DataN3 rec1 = check parseString(xmlStr1);
    test:assertEquals(rec1.A, [1, 2, 3, 0]);
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordWithArrayAsFieldType() returns error? {
    xml xmlVal1 = xml `<Data><A>1</A><A>2</A><A>3</A></Data>`;
    DataN3 rec1 = check parseAsType(xmlVal1);
    test:assertEquals(rec1.A, [1, 2, 3, 0]);
}

type Student record {|
    string name;
    int age;
    string code = "Admitted";
|};

@test:Config
function testXmlToRecordWithDefaultValuesForParseString1() returns error? {
    string xmlStr = string `
    <Student>
        <name>Walter</name>
        <age>27</age>
    </Student>
    `;

    Student student = check parseString(xmlStr);
    test:assertEquals(student.name, "Walter");
    test:assertEquals(student.age, 27);
    test:assertEquals(student.code, "Admitted");
}

@test:Config
function testXmlToRecordWithDefaultValuesForPasrseAsType1() returns error? {
    xml xmlVal = xml `
    <Student>
        <name>Walter</name>
        <age>27</age>
    </Student>
    `;

    Student student = check parseAsType(xmlVal);
    test:assertEquals(student.name, "Walter");
    test:assertEquals(student.age, 27);
    test:assertEquals(student.code, "Admitted");
}

type University record {|
    Student[] student;
    string name;
    string category = "State"; 
|};

@test:Config
function testXmlToRecordWithDefaultValuesForParseString2() returns error? {
    string xmlStr = string `
    <University>
        <student>
            <name>Walter</name>
            <age>27</age>
        </student>
        <student>
            <name>Jessy</name>
            <age>18</age>
        </student>
        <name>Standford</name>
    </University>
    `;

    University university = check parseString(xmlStr);
    test:assertEquals(university.student[0].name, "Walter");
    test:assertEquals(university.student[0].age, 27);
    test:assertEquals(university.student[0].code, "Admitted");

    test:assertEquals(university.student[1].name, "Jessy");
    test:assertEquals(university.student[1].age, 18);
    test:assertEquals(university.student[1].code, "Admitted");

    test:assertEquals(university.name, "Standford");
    test:assertEquals(university.category, "State");
}

@test:Config
function testXmlToRecordWithDefaultValuesForParseAsType2() returns error? {
    xml xmlVal = xml `
    <University>
        <student>
            <name>Walter</name>
            <age>27</age>
        </student>
        <student>
            <name>Jessy</name>
            <age>18</age>
        </student>
        <name>Standford</name>
    </University>
    `;

    University university = check parseAsType(xmlVal);
    test:assertEquals(university.student[0].name, "Walter");
    test:assertEquals(university.student[0].age, 27);
    test:assertEquals(university.student[0].code, "Admitted");

    test:assertEquals(university.student[1].name, "Jessy");
    test:assertEquals(university.student[1].age, 18);
    test:assertEquals(university.student[1].code, "Admitted");

    test:assertEquals(university.name, "Standford");
    test:assertEquals(university.category, "State");
}

// Negative cases
type DataN1 record {|
    int A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative1() {
    string xmlStr1 = "<Data><B></B></Data>";
    DataN1|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "required field 'A' not present in XML");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative1() {
    xml xmlVal1 = xml `<Data><B></B></Data>`;
    DataN1|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "required field 'A' not present in XML");
}

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative2() {
    string xmlStr1 = "<Data><A>1.0</A></Data>";
    DataN1|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "'string' value '1.0' cannot be converted to 'int'");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative2() {
    xml xmlVal1 = xml `<Data><A>1.0</A></Data>`;
    DataN1|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "'string' value '1.0' cannot be converted to 'int'");
}

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative4() {
    string xmlStr1 = "<Data><A>1</A><A>2</A></Data>";
    DataN1|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "expected 'int' value for the field 'A' found 'array' value");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative4() {
    xml xmlVal1 = xml `<Data><A>1</A><A>2</A></Data>`;
    DataN1|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "expected 'int' value for the field 'A' found 'array' value");
}

type DataN4 record {|
    string...;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative6() {
    string xmlStr1 = "<Data><A>1</A><A>2</A><A>3</A></Data>";
    DataN4|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "expected 'string' value for the field 'A' found 'array' value");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative6() {
    xml xmlVal1 = xml `<Data><A>1</A><A>2</A><A>3</A></Data>`;
    DataN4|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "expected 'string' value for the field 'A' found 'array' value");
}

@Name {
    value: "Space"
}
type DataN5 record {|
    string A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative7() {
    string xmlStr1 = "<Data><A>1</A></Data>";
    DataN5|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "record type name 'Space' mismatch with given XML name 'Data'");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative7() {
    xml xmlVal1 = xml `<Data><A>1</A></Data>`;
    DataN5|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "record type name 'Space' mismatch with given XML name 'Data'");
}

@Namespace {
    uri: "www.example.com"
}
type DataN6 record {|
    string A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative8() {
    string xmlStr1 = string `<Data xmlns="www.test.com"><A>1</A></Data>`;
    DataN6|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "namespace mismatched for the type: 'DataN6'");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative8() {
    xml xmlVal1 = xml `<Data xmlns="www.test.com"><A>1</A></Data>`;
    DataN6|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "namespace mismatched for the type: 'DataN6'");
}

type DataN7 record {|
    @Namespace {
        uri: "www.example.com"
    }
    string A;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative9() {
    string xmlStr1 = string `<Data xmlns:ns1="www.test.com"><ns1:A>1</ns1:A></Data>`;
    DataN7|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "required field 'A' not present in XML");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative9() {
    xml xmlVal1 = xml `<Data xmlns:ns1="www.test.com"><ns1:A>1</ns1:A></Data>`;
    DataN7|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "required field 'A' not present in XML");
}

@Namespace {
    prefix: "x",
    uri: "example.com"
}
type DataN8 record {|
    @Namespace {
        uri: "example.com"
    }
    string bar;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative10() {
    string xmlStr1 = string `<x:foo xmlns:x="example.com"><x:bar>1</x:bar></x:foo>`;
    DataN8|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "required field 'bar' not present in XML");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative10() {
    xml xmlVal1 = xml `<x:foo xmlns:x="example.com"><x:bar>1</x:bar></x:foo>`;
    DataN8|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "required field 'bar' not present in XML");
}

@Namespace {
    prefix: "x",
    uri: "example.com"
}
type DataN9 record {|
    @Namespace {
        prefix: "x",
        uri: "example.com"
    }
    record {|
        @Namespace {
            uri: "example.com"
        }
        string baz;
    |} bar;
|};

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative11() {
    string xmlStr1 = string `<x:foo xmlns:x="example.com" xmlns="example2.com"><x:bar><baz>2</baz></x:bar></x:foo>`;
    DataN9|error rec1 = parseString(xmlStr1);
    test:assertEquals((<error>rec1).message(), "required field 'baz' not present in XML");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative11() {
    xml xmlVal1 = xml `<x:foo xmlns:x="example.com" xmlns="example2.com"><x:bar><baz>2</baz></x:bar></x:foo>`;
    DataN9|error rec1 = parseAsType(xmlVal1);
    test:assertEquals((<error>rec1).message(), "required field 'baz' not present in XML");
}

@test:Config {
    groups: ["fromXmlString"]
}
function testXmlStringToRecordNegative12() {
    string xmlStr = string `<Data>
        <A>1</A>
        <B data="dataValue2">2</B>
    </Data>`;

    RecAtt6|error rec = parseString(xmlStr);
    test:assertEquals((<error>rec).message(), "required attribute 'data' not present in XML");
}

@test:Config {
    groups: ["fromXml"]
}
function testXmlToRecordNegative12() {
    xml xmlVal = xml `<Data>
        <A>1</A>
        <B data="dataValue2">2</B>
    </Data>`;

    RecAtt6|error rec = parseAsType(xmlVal);
    test:assertEquals((<error>rec).message(), "required attribute 'data' not present in XML");
}

@test:Config {
    groups: ["fromXmlString"]
}
function testCommentMiddleInContentNegative1() {
    string xmlStr = string `<Data><A>1<!-- cmt -->2</A></Data>`;
    record {|
        int A;
    |}|error rec1 = parseString(xmlStr);
    test:assertEquals((<error>rec1).message(), "invalid type expected 'int' but found 'string'");

    record {|
        int...;
    |}|error rec2 = parseString(xmlStr);
    test:assertEquals((<error>rec2).message(), "invalid type expected 'int' but found 'string'");
}

@test:Config {
    groups: ["fromXml"]
}
function testCommentMiddleInContentNegative2() {
    xml xmlVal = xml `<Data><A>1<!-- cmt -->2</A></Data>`;
    record {|
        int A;
    |}|error rec1 = parseAsType(xmlVal);
    test:assertEquals((<error>rec1).message(), "invalid type expected 'int' but found 'string'");

    record {|
        int...;
    |}|error rec2 = parseAsType(xmlVal);
    test:assertEquals((<error>rec2).message(), "invalid type expected 'int' but found 'string'");
}

@test:Config {
    groups: ["fromXml"]
}
function testUnsupportedTypeNegative() {
    xml xmlVal1 = xml `
    <Data>
        <A>1</A>
        <A>2</A>
    </Data>
    `;
    record {|
        xml[] A;
    |}|error err1 = parseAsType(xmlVal1);
    test:assertEquals((<error>err1).message(), "unsupported input type");

    xml xmlVal2 = xml `
    <Data>
        <A>
            <a>1</a>
        </A>
    </Data>
    `;
    record {|
        record {|string a;|}|record {|string b;|} A;
    |}|error err2 = parseAsType(xmlVal2);
    test:assertEquals((<error>err2).message(), "unsupported input type");

    record {|
        record {|string a;|}? A;
    |}|error err3 = parseAsType(xmlVal2);
    test:assertEquals((<error>err3).message(), "unsupported input type");
}

@Namespace {
    uri: "http://example.com/book"
}
type OpenBook1 record {
    @Namespace {
        uri: "http://example.com"
    }
    int id;
    @Namespace {
        uri: "http://example.com/book"
    }
    string title;
    @Namespace {
        uri: "http://example.com/book"
    }
    string author;
};

@test:Config
function testInvalidNamespaceInOpenRecordForParseString1() {
    string xmldata = string `
    <book xmlns="http://example.com/book">
        <id>601970</id>
        <title>string</title>
        <author>string</author>
    </book>`;
    OpenBook1|Error err = parseString(xmldata);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "undefined field 'id' in record 'data.xmldata:OpenBook1'");
}

@test:Config
function testInvalidNamespaceInOpenRecordForParseAsType1() {
    xml xmldata = xml `
    <book xmlns="http://example.com/book">
        <id>601970</id>
        <title>string</title>
        <author>string</author>
    </book>`;
    OpenBook1|Error err = parseAsType(xmldata);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "undefined field 'id' in record 'data.xmldata:OpenBook1'");
}

@Namespace {
    uri: "http://example.com/book"
}
type OpenBook2 record {
    AuthorOpen author;
    @Namespace {
        uri: "http://example.com/book"
    }
    string title;
};

type AuthorOpen record {
    @Namespace {
        uri: "http://example.com"
    }
    string name;
    @Namespace {
        uri: "http://example.com/book"
    }
    int age;
};

@test:Config
function testInvalidNamespaceInOpenRecordForParseString2() {
    string xmldata = string `
    <book xmlns="http://example.com/book">
        <author>
            <name>R.C Martin</name>
            <age>60</age>
        </author>
        <title>Clean Code</title>
    </book>`;
    OpenBook2|Error err = parseString(xmldata);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "undefined field 'name' in record 'data.xmldata:AuthorOpen'");
}

@test:Config
function testInvalidNamespaceInOpenRecordForParseAsType2() {
    xml xmldata = xml `
    <book xmlns="http://example.com/book">
        <author>
            <name>R.C Martin</name>
            <age>60</age>
        </author>
        <title>Clean Code</title>
    </book>`;
    OpenBook2|Error err = parseAsType(xmldata);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "undefined field 'name' in record 'data.xmldata:AuthorOpen'");
}

@test:Config 
function testRegexAsFieldTypeWithParseStringNegative1() {
    string xmlStr = string `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code</B>
                    <C>
                        <name>Kanth</name>
                    </C>
                </Data>`;
    record {|
        string:RegExp[] A;
        string B;
        record {|
            string name;
        |} C;
    |}|Error err = parseString(xmlStr);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "unsupported input type");
}

@test:Config 
function testRegexAsFieldTypeWithParseStringNegative2() {
    string xmlStr = string `<Data>
                    <A>1</A>
                    <B>Code</B>
                    <C>
                        <name>Kanth</name>
                    </C>
                </Data>`;
    record {|
        string:RegExp A;
        string B;
        record {|
            string name;
        |} C;
    |}|Error err = parseString(xmlStr);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "unsupported input type");
}

@test:Config
function testRegexAsFieldTypeWithParseAsType() {
    xml xmlVal = xml `<Data>
                    <A>1</A>
                    <A>2</A>
                    <B>Code</B>
                    <C>
                        <name>Kanth</name>
                    </C>
                </Data>`;
    record {|
        string:RegExp[] A;
        string B;
        record {|
            string name;
        |} C;
    |}|error err = parseAsType(xmlVal);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "unsupported input type");
}

@test:Config
function testRegexAsFieldTypeWithParseAsType2() {
    xml xmlVal = xml `<Data>
                    <A>1</A>
                    <B>Code</B>
                    <C>
                        <name>Kanth</name>
                    </C>
                </Data>`;
    record {|
        string:RegExp A;
        string B;
        record {|
            string name;
        |} C;
    |}|error err = parseAsType(xmlVal);
    test:assertTrue(err is error);
    test:assertEquals((<error>err).message(), "unsupported input type");
}