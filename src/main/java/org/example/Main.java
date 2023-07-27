package org.example;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        CrptApi crptApi = new CrptApi();
        CrptApi.DummyDoc doc = new CrptApi.DummyDoc("TEST_DOC_ID_0");
        crptApi.createDocumentCommissioningContract(doc, "TEST_SING");
    }
}