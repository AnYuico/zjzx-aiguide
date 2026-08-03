package com.tzp.zjzx.agent.client;

import com.tzp.zjzx.agent.exception.AgentAuthenticationException;
import com.tzp.zjzx.agent.exception.PersonalActionRejectedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonalDataHttpClientTest {

    @Test
    void resolvesPrincipalWithInternalAndMallTokens() {
        List<ClientRequest> requests = new ArrayList<>();
        ExchangeFunction userExchange = request -> {
            requests.add(request);
            return json(HttpStatus.OK, """
                    {
                      "userId": 33,
                      "nickName": "test"
                    }
                    """);
        };
        PersonalDataHttpClient client = client(
                userExchange,
                request -> json(HttpStatus.OK, "[]"),
                request -> json(HttpStatus.OK, "[]")
        );

        StepVerifier.create(client.resolvePrincipal("mall-login-token"))
                .assertNext(principal -> {
                    assertEquals(33L, principal.getUserId());
                    assertEquals("test", principal.getNickName());
                })
                .verifyComplete();

        ClientRequest request = requests.get(0);
        assertEquals("/api/user/userInfo/internal/agent/current",
                request.url().getPath());
        assertEquals("internal-secret",
                request.headers().getFirst(PersonalDataHttpClient.INTERNAL_TOKEN_HEADER));
        assertEquals("mall-login-token",
                request.headers().getFirst(PersonalDataHttpClient.MALL_TOKEN_HEADER));
    }

    @Test
    void mapsInvalidMallTokenToAuthenticationFailure() {
        PersonalDataHttpClient client = client(
                request -> Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED).build()),
                request -> json(HttpStatus.OK, "[]"),
                request -> json(HttpStatus.OK, "[]")
        );

        StepVerifier.create(client.resolvePrincipal("expired-token"))
                .expectError(AgentAuthenticationException.class)
                .verify();
    }

    @Test
    void readsSanitizedCartAndRecentOrderEndpoints() {
        List<ClientRequest> requests = new ArrayList<>();
        ExchangeFunction cartExchange = request -> {
            requests.add(request);
            return json(HttpStatus.OK, """
                    [{
                      "skuId": 14,
                      "skuName": "Mac mini 16G",
                      "cartPrice": 1999.00,
                      "quantity": 1,
                      "selected": true
                    }]
                    """);
        };
        ExchangeFunction orderExchange = request -> {
            requests.add(request);
            return json(HttpStatus.OK, """
                    [{
                      "recentPosition": 1,
                      "status": "WAITING_PAYMENT",
                      "statusText": "Waiting for payment",
                      "totalAmount": 1999.00,
                      "productNames": ["Mac mini 16G"]
                    }]
                    """);
        };
        PersonalDataHttpClient client = client(
                request -> json(HttpStatus.OK, "{}"),
                cartExchange,
                orderExchange
        );

        StepVerifier.create(client.getCart(33L))
                .assertNext(items -> {
                    assertEquals(1, items.size());
                    assertEquals(14L, items.get(0).getSkuId());
                })
                .verifyComplete();
        StepVerifier.create(client.listRecentOrders(
                        33L,
                        "WAITING_PAYMENT",
                        5
                ))
                .assertNext(orders -> {
                    assertEquals(1, orders.size());
                    assertEquals("WAITING_PAYMENT", orders.get(0).getStatus());
                })
                .verifyComplete();

        assertEquals("/api/order/cart/internal/agent/users/33",
                requests.get(0).url().getPath());
        assertEquals("/api/order/orderInfo/internal/agent/users/33/recent",
                requests.get(1).url().getPath());
        assertEquals("status=WAITING_PAYMENT&limit=5",
                requests.get(1).url().getQuery());
    }

    @Test
    void postsIdempotentCartMutationWithoutMallIdentityArguments() {
        List<ClientRequest> requests = new ArrayList<>();
        ExchangeFunction cartExchange = request -> {
            requests.add(request);
            return json(HttpStatus.OK, """
                    {
                      "applied": true,
                      "replayed": false
                    }
                    """);
        };
        PersonalDataHttpClient client = client(
                request -> json(HttpStatus.OK, "{}"),
                cartExchange,
                request -> json(HttpStatus.OK, "[]")
        );

        StepVerifier.create(client.addCartItem(
                        33L,
                        "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
                        14L,
                        2
                ))
                .assertNext(result -> {
                    assertEquals(true, result.getApplied());
                    assertEquals(false, result.getReplayed());
                })
                .verifyComplete();

        ClientRequest request = requests.get(0);
        assertEquals("/api/order/cart/internal/agent/users/33/items",
                request.url().getPath());
        assertEquals("internal-secret", request.headers()
                .getFirst(PersonalDataHttpClient.INTERNAL_TOKEN_HEADER));
    }

    @Test
    void resolvesCandidateAndPostsIdempotentOrderCancellation() {
        List<ClientRequest> requests = new ArrayList<>();
        ExchangeFunction orderExchange = request -> {
            requests.add(request);
            if ("GET".equals(request.method().name())) {
                return json(HttpStatus.OK, """
                        {
                          "recentPosition": 1,
                          "orderNo": "internal-order-61",
                          "totalAmount": 1999.00,
                          "createdAt": "2026-07-29 18:00:00",
                          "productNames": ["Mac mini 16G"]
                        }
                        """);
            }
            return json(HttpStatus.OK, """
                    {
                      "applied": true,
                      "replayed": false
                    }
                    """);
        };
        PersonalDataHttpClient client = client(
                request -> json(HttpStatus.OK, "{}"),
                request -> json(HttpStatus.OK, "[]"),
                orderExchange
        );

        StepVerifier.create(client.getCancellationCandidate(33L, 1))
                .assertNext(candidate -> {
                    assertEquals(1, candidate.getRecentPosition());
                    assertEquals("internal-order-61", candidate.getOrderNo());
                })
                .verifyComplete();
        StepVerifier.create(client.cancelOrder(
                        33L,
                        "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
                        "internal-order-61"
                ))
                .assertNext(result -> {
                    assertEquals(true, result.getApplied());
                    assertEquals(false, result.getReplayed());
                })
                .verifyComplete();

        assertEquals(
                "/api/order/orderInfo/internal/agent/users/33"
                        + "/cancellation-candidates/1",
                requests.get(0).url().getPath()
        );
        assertEquals(
                "/api/order/orderInfo/internal/agent/users/33/cancellations",
                requests.get(1).url().getPath()
        );
        assertEquals("internal-secret", requests.get(1).headers()
                .getFirst(PersonalDataHttpClient.INTERNAL_TOKEN_HEADER));
    }

    @Test
    void mapsOrderStateChangeToActionRejection() {
        PersonalDataHttpClient client = client(
                request -> json(HttpStatus.OK, "{}"),
                request -> json(HttpStatus.OK, "[]"),
                request -> json(HttpStatus.CONFLICT, """
                        {
                          "code": "CONFLICT"
                        }
                        """)
        );

        StepVerifier.create(client.cancelOrder(
                        33L,
                        "d0b2abec-b950-4a6f-94f6-8f54647d2db6",
                        "internal-order-61"
                ))
                .expectError(PersonalActionRejectedException.class)
                .verify();
    }

    private PersonalDataHttpClient client(
            ExchangeFunction userExchange,
            ExchangeFunction cartExchange,
            ExchangeFunction orderExchange) {
        return new PersonalDataHttpClient(
                webClient("http://127.0.0.1:8512", userExchange),
                webClient("http://127.0.0.1:8513", cartExchange),
                webClient("http://127.0.0.1:8514", orderExchange),
                "internal-secret",
                Duration.ofSeconds(1)
        );
    }

    private WebClient webClient(String baseUrl, ExchangeFunction exchangeFunction) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeFunction(exchangeFunction)
                .build();
    }

    private Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }
}
