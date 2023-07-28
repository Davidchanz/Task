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
import java.util.Base64;

public class CrptApi {
    private static final String LOCATION = "https://ismp.crpt.ru/";
    private static final String CREATE_DOCUMENT_COMMISSIONING_CONTRACT_REQUEST = "api/v3/lk/documents/commissioning/contract/create";
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
                .headers("Authorization", "Bearer " + Authorization.getAuthorization().getPrincipal().getToken(),
                        "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response;
        try {
           response = client.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (IOException | InterruptedException e) {
            //log
           String message = "Error while request to '" + request.uri().toString() +
                    "'\nMethod: '" + request.method() +
                    "'\nHeaders: '" + request.headers().toString() +
                    "'\nBody: '" + (request.bodyPublisher().isPresent() ? request.bodyPublisher().get() : "empty") + "'";
            System.err.println(message);
            throw new HTTPRequestException(message, e);
        }
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

    private static class Authorization{
        private static final String GET_AUTH_SERT_REQUEST = "api/v3/auth/cert/";
        private static final String GET_SERT_KEY_REQUEST = "api/v3/auth/cert/key";

        @Getter
        private Principal principal;
        private static Authorization authorization;
        private Authorization() {
            this.getNewPrincipal();
        }

        private void getNewPrincipal(){
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LOCATION + GET_SERT_KEY_REQUEST))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException | InterruptedException e) {
                //log
                String message = "Error while request to '" + request.uri().toString() +
                        "'\nMethod: '" + request.method() +
                        "'\nHeaders: '" + request.headers().toString() +
                        "'\nBody: '" + (request.bodyPublisher().isPresent() ? request.bodyPublisher().get() : "empty") + "'";
                System.err.println(message);
                throw new HTTPRequestException(message, e);
            }
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

        public static Authorization getAuthorization() {
            if (authorization == null) {
                synchronized (Authorization.class) {
                    if(authorization == null) {
                        authorization = new Authorization();
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
            public String getToken() {
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
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response;
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (IOException | InterruptedException e) {
                    //log
                    String message = "Error while request to '" + request.uri().toString() +
                            "'\nMethod: '" + request.method() +
                            "'\nHeaders: '" + request.headers().toString() +
                            "'\nBody: '" + (request.bodyPublisher().isPresent() ? request.bodyPublisher().get() : "empty") + "'";
                    System.err.println(message);
                    throw new HTTPRequestException(message, e);
                }
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
