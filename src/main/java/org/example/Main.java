package org.example;

import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 1);
        CrptApi.DummyDoc doc = new CrptApi.DummyDoc("TEST_DOC_ID_0");
        for(int i = 0; i < 2; i++) {
            new Thread(() -> {
                crptApi.createDocumentCommissioningContract(doc, "TEST_SING");
            }).start();
        }
    }
}