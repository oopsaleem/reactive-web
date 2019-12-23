package com.example.demo;

import lombok.extern.log4j.Log4j2;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
//There are no slices in this test. We’re starting up the whole application. Spring Boot lets us still exercise some
// control over things like the port to which the application binds when it starts.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class WebSocketConfigurationTest {
    //Spring WebFlux provides a reactive WebSocketClient that we’ll use to consume messages coming off of the
    // websocket stream.
    private final WebSocketClient socketClient = new ReactorNettyWebSocketClient();

    //Spring WebFlux also provides a reactive HTTP client, perfect for talking to other microservices.
    private final WebClient webClient = WebClient.builder().build();

    //We’re going to generate some random data and have it written to our MongoDB repository.
    private Profile generateRandomProfile() {
        return new Profile(UUID.randomUUID().toString(), UUID.randomUUID().toString() + "@email.com");
    }

    @Test
    public void testNotificationsOnUpdates() throws Exception {

        //The plan is to write ten items using the POST endpoint in our API. We’ll first subscribe to the
        // websocket endpoint and then we’ll start consuming and confirm that we’ve got ten records.
        int count = 10;
        //The websocket notifications will come in asynchronously, so we will use a Java AtomicLong to capture the
        // count in a thread-safe manner.
        AtomicLong counter = new AtomicLong();
        //Note that we’re talking to a ws:// endpoint, not an http:// endpoint.
        URI uri = URI.create("ws://localhost:8080/ws/profiles");
        //The socketClient lets us subscribe to the websocket endpoint. It returns a Publisher<T> which this test
        // promptly then subscribes to.
        socketClient.execute(uri, (WebSocketSession session) -> {
            //We send a throw away message to get the conversation started…​
            Mono<WebSocketMessage> out = Mono.just(session.textMessage("test"));
            //Then we setup a reactive pipeline to subscribe to any incoming messages coming in from the websocket
            // endpoint as a WebSocketMessage endpoint whose String contents we unpack.
            Flux<String> in = session
                    .receive()
                    .map(WebSocketMessage::getPayloadAsText);
            //We use the WebSocketSession to write and receive data. For each item that’s returned we increment
            // our AtomicLong.
            return session
                    .send(out)
                    .thenMany(in)
                    .doOnNext(str -> counter.incrementAndGet())
                    .then();
        }).subscribe();
        //Now that the websocket subscriber is up and running, we create a pipeline of elements that gets limited to
        // count elements (10) and then issue count HTTP POST writes to the API using the reactive WebClient. We use
        // blockLast() to force the writes to happen before we proceed to the next line where we compare consumed records.
        Flux
                .<Profile>generate(sink -> sink.next(generateRandomProfile()))
                .take(count)
                .flatMap(this::write)
                .blockLast();

        Thread.sleep(1000);

        //Finally, after all the writes have occurred and another second of padding to spare has elapsed, we confirm
        // that we’ve seen count notifications for our count writes.
        Assertions.assertThat(counter.get()).isEqualTo(count);
    }

    private Publisher<Profile> write(Profile p) {
        return
                this.webClient
                        .post()
                        .uri("http://localhost:8080/profiles")
                        .body(BodyInserters.fromObject(p))
                        .retrieve()
                        .bodyToMono(String.class)
                        .thenReturn(p);
    }
}