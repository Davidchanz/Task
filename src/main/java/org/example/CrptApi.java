package org.example;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private static final String LOCATION = "https://ismp.crpt.ru/";
    private static final String CREATE_DOCUMENT_COMMISSIONING_CONTRACT_REQUEST = "api/v3/lk/documents/commissioning/contract/create";
    private final List<BucketToken> requestBucket = Collections.synchronizedList(new ArrayList<>());
    private int requestLimit;
    private TimeUnit timeUnit;
    private static class BucketToken{

    }
    public CrptApi(TimeUnit timeUnit, int requestLimit){
        this.requestLimit = requestLimit;
        this.timeUnit = timeUnit;
        new Thread(() -> {
            synchronized (requestBucket) {
                while (true) {
                    requestBucket.clear();
                    for (int i = 0; i < requestLimit; i++)
                        requestBucket.add(new BucketToken());
                    try {
                        requestBucket.wait(TimeUnit.MINUTES.toMillis(requestLimit));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }).start();
    }

    private synchronized HttpResponse<String> sendRequest(HttpRequest request){
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;
        try {
            if(!requestBucket.isEmpty()) {
                System.out.println(Thread.currentThread().toString() + " request...");
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } else {
                throw new NumberAPIRequestsExceeded("Number of requests to API exceeded! Max: " + this.requestLimit + " per " + this.timeUnit.toString());
            }
        } catch (IOException | InterruptedException e) {
            //log
            String message = "Error while request to '" + request.uri().toString() +
                    "'\nMethod: '" + request.method() +
                    "'\nHeaders: '" + request.headers().toString() +
                    "'\nBody: '" + (request.bodyPublisher().isPresent() ? request.bodyPublisher().get() : "empty") + "'";
            System.out.println(message);
            throw new HTTPRequestException(message, e);
        }finally {
            if(!requestBucket.isEmpty())
                requestBucket.remove(0);
        }
        return response;
    }
    public APIResponse createDocumentCommissioningContract(Document document, String signature) {
        ObjectMapper mapper = new ObjectMapper();
        DocumentCommissioningContractRequestBody requestBody = new DocumentCommissioningContractRequestBody(DocumentFormat.MANUAL, document,
                signature, DocumentType.LP_INTRODUCE_GOODS);
        String jsonBody;
        try {
            jsonBody = mapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            //log
            String message = "Error while mapping in JSON '" + DocumentCommissioningContractRequestBody.class + "' " + requestBody;
            System.err.println(message);
            throw new JSONMappingException(message, e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION + CREATE_DOCUMENT_COMMISSIONING_CONTRACT_REQUEST))
                .headers("Authorization", "Bearer " + Authorization.getAuthorization(this).getPrincipal().getToken(this),
                        "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = this.sendRequest(request);
        if (response.statusCode() == HttpURLConnection.HTTP_OK) {
            try {
                return mapper.readValue(response.body(), OKResponse.class);
            } catch (JsonProcessingException e) {
                //log
                String message = "Error while mapping in JSON '" + OKResponse.class + "' " + response.body();
                System.err.println(message);
                throw new JSONMappingException(message, e);
            }
        }else {
            try {
                return mapper.readValue(response.body(), BadResponse.class);
            } catch (JsonProcessingException e) {
                //log
                String message = "Error while mapping in JSON '" + BadResponse.class + "' " + response.body();
                System.err.println(message);
                throw new JSONMappingException(message, e);
            }
        }
    }

    private abstract static class MappingException extends RuntimeException{
        public MappingException(String message, Throwable ex){
            super(message, ex);
        }
    }

    private static class JSONMappingException extends MappingException{
        public JSONMappingException(String message, Throwable ex){
            super(message, ex);
        }
    }

    private static class HTTPRequestException extends RuntimeException{
        public HTTPRequestException(String message, Throwable ex){
            super(message, ex);
        }
    }

    private static class NumberAPIRequestsExceeded extends RuntimeException{
        public NumberAPIRequestsExceeded(String message){
            super(message);
        }
    }

    private static class Authorization{
        private static final String GET_AUTH_SERT_REQUEST = "api/v3/auth/cert/";
        private static final String GET_SERT_KEY_REQUEST = "api/v3/auth/cert/key";
        @Getter
        private Principal principal;
        private static Authorization authorization;
        private Authorization() {
        }

        private Authorization(CrptApi crptApi) {
            this.getNewPrincipal(crptApi);
        }

        private void getNewPrincipal(CrptApi crptApi){
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LOCATION + GET_SERT_KEY_REQUEST))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = crptApi.sendRequest(request);
            ObjectMapper mapper = new ObjectMapper();
            try {
                principal = mapper.readValue(response.body(), Principal.class);
            } catch (JsonProcessingException e) {
                //log
                String message = "Error while mapping in JSON '" + Principal.class + "' " + response.body();
                System.err.println(message);
                throw new JSONMappingException(message, e);
            }
        }

        public static Authorization getAuthorization(CrptApi crptApi) {
            if (authorization == null) {
                synchronized (Authorization.class) {
                    if(authorization == null) {
                        authorization = new Authorization(crptApi);
                    }

                }
            }
            return authorization;
        }

        @Getter
        @Setter
        private static class Token{
            private final String token;

            public Token(String token) {
                this.token = token;
            }
        }

        @Getter
        @Setter
        public static class Principal{
            private String uuid;
            private String data;

            public Principal(){}

            public Principal(String uuid, String data) {
                this.uuid = uuid;
                this.data = data;
            }

            @JsonIgnore
            public String getToken(CrptApi crptApi) {
                ObjectMapper mapper = new ObjectMapper();
                String jsonBody;
                try {
                    jsonBody = mapper.writeValueAsString(this);//https://markirovka.demo.crpt.tech/ - не работает! Я не могу получить УКЭП и подписать данные
                } catch (JsonProcessingException e) {
                    //log
                    String message = "Error while mapping in JSON '" + Principal.class + "' " + this;
                    System.err.println(message);
                    throw new JSONMappingException(message, e);
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LOCATION + GET_AUTH_SERT_REQUEST))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpResponse<String> response = crptApi.sendRequest(request);
                try {
                    return mapper.readValue(response.body(), Token.class).getToken();
                } catch (JsonProcessingException e) {
                    //log
                    String message = "Error while mapping in JSON '" + Token.class + "' " + response.body();
                    System.err.println(message);
                    throw new JSONMappingException(message, e);
                }
            }
        }
    }

    @Getter
    @Setter
    private static class DocumentCommissioningContractRequestBody{
        private DocumentFormat documentFormat;
        private String productDocument;
        private String productGroup;
        private String signature;
        private DocumentType type;

        public DocumentCommissioningContractRequestBody(DocumentFormat documentFormat,
                           Document productDocument,
                           String productGroup,
                           String signature,
                           DocumentType type) {
            this.documentFormat = documentFormat;
            ObjectMapper mapper = new ObjectMapper();
            String productDocumentString;
            try {
                productDocumentString = mapper.writeValueAsString(productDocument);
            } catch (JsonProcessingException e) {
                //log
                String message = "Error while mapping in JSON '" + Document.class + "' " + productDocument;
                System.err.println(message);
                throw new JSONMappingException(message, e);
            }
            this.productDocument = Base64.getEncoder().encodeToString(productDocumentString.getBytes());
            this.productGroup = productGroup;
            this.signature = Base64.getEncoder().encodeToString(signature.getBytes());
            this.type = type;
        }

        public DocumentCommissioningContractRequestBody(DocumentFormat documentFormat,
                           Document productDocument,
                           String signature,
                           DocumentType type) {
            this.documentFormat = documentFormat;
            ObjectMapper mapper = new ObjectMapper();
            String productDocumentString;
            try {
                productDocumentString = mapper.writeValueAsString(productDocument);
            } catch (JsonProcessingException e) {
                //log
                String message = "Error while mapping in JSON '" + Document.class + "' " + productDocument;
                System.err.println(message);
                throw new JSONMappingException(message, e);
            }
            this.productDocument = Base64.getEncoder().encodeToString(productDocumentString.getBytes());
            this.signature = signature;
            this.type = type;
        }

        @Override
        public String toString() {
            return "DocumentCommissioningContractRequestBody{" +
                    "documentFormat=" + documentFormat +
                    ", productDocument='" + productDocument + '\'' +
                    ", productGroup='" + productGroup + '\'' +
                    ", signature='" + signature + '\'' +
                    ", type=" + type +
                    '}';
        }
    }

    private interface APIResponse{
        String getResponseCode();
        default String getResponseBody() throws JsonProcessingException {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        }
    }
    private static class OKResponse implements APIResponse{
        private String value;

        @Override
        public String getResponseCode() {
            return String.valueOf(HttpURLConnection.HTTP_OK);
        }
    }
    private static class BadResponse implements APIResponse{
        private String code;
        private String errorMessage;
        private String description;

        @Override
        public String getResponseCode() {
            return this.code;
        }
    }

    private static abstract class Document{

    }

    @Getter
    @Setter
    public static class DummyDoc extends Document{
        private String docId;

        public DummyDoc(String docId) {
            this.docId = docId;
        }
    }

    private enum DocumentFormat{
        MANUAL,
        XML,
        CSS
    }

    private enum DocumentType{
        LP_INTRODUCE_GOODS,
        LP_INTRODUCE_GOODS_XML,
        LP_INTRODUCE_GOODS_CSV
    }
}
