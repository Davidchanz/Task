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
    private static final String LOCATION = "https://ismp.crpt.ru/";//https://markirovka.demo.crpt.tech/ - dont work
    private static final String CREATE_DOCUMENT_COMMISSIONING_CONTRACT_REQUEST = "api/v3/lk/documents/commissioning/contract/create";
    public APIResponse createDocumentCommissioningContract(Document document, String signature) {
        ObjectMapper mapper = new ObjectMapper();
        DocumentCommissioningContractRequestBody requestBody = new DocumentCommissioningContractRequestBody(DocumentFormat.MANUAL, document,
                signature, DocumentType.LP_INTRODUCE_GOODS);
        String jsonBody = null;
        try {
            jsonBody = mapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LOCATION + CREATE_DOCUMENT_COMMISSIONING_CONTRACT_REQUEST))
                .headers("Authorization", "Bearer " + CrptApi.Authorization.getPrincipal().getToken(),
                        "Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if(response.statusCode() == HttpURLConnection.HTTP_OK){
                return mapper.readValue(response.body(), OKResponse.class);
            }else
                return mapper.readValue(response.body(), BadResponse.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Authorization{
        private static final String GET_AUTH_SERT_REQUEST = "api/v3/auth/cert/";

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
                String jsonBody = null;
                try {
                    jsonBody = mapper.writeValueAsString(new Principal(this.uuid, Base64.getEncoder().encodeToString(this.data.getBytes())));
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LOCATION + GET_AUTH_SERT_REQUEST))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();
                HttpClient client = HttpClient.newHttpClient();
                try {
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    return mapper.readValue(response.body(), Token.class).getToken();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        private static final String GET_SERT_KEY_REQUEST = "api/v3/auth/cert/key";
        private static Principal principal;

        private Authorization() {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LOCATION + GET_SERT_KEY_REQUEST))
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response;
            try {
                response = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            ObjectMapper mapper = new ObjectMapper();
            try {
                principal = mapper.readValue(response.body(), Principal.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        public static Principal getPrincipal() {
            if (principal == null) {
                synchronized (Authorization.class) {
                    if(principal==null) {
                        new Authorization();
                    }

                }
            }
            return principal;
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
                throw new RuntimeException(e);
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
                throw new RuntimeException(e);
            }
            this.productDocument = Base64.getEncoder().encodeToString(productDocumentString.getBytes());
            this.signature = signature;
            this.type = type;
        }
    }

    private interface APIResponse{
        String getResponseCode();// return HTTP response code
        default String getResponseBody() throws JsonProcessingException {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this);
        };// return response in JSON string format
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
