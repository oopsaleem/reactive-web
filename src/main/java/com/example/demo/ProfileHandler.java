package com.example.demo;

import org.reactivestreams.Publisher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
class ProfileHandler {
    //as before, we’re going to make use of our ProfileService to do the heavy lifting
    private final ProfileService profileService;

    ProfileHandler(ProfileService profileService) {
        this.profileService = profileService;
    }

    //	Each handler method has an identical signature: ServerRequest is the request parameter and
    //	Mono<ServerResponse> is the return value.
    Mono<ServerResponse> getById(ServerRequest r) {
        return defaultReadResponse(this.profileService.get(id(r)));
    }

    Mono<ServerResponse> all(ServerRequest r) {
        return defaultReadResponse(this.profileService.all());
    }

    Mono<ServerResponse> deleteById(ServerRequest r) {
        return defaultReadResponse(this.profileService.delete(id(r)));
    }

    Mono<ServerResponse> updateById(ServerRequest r) {
        Flux<Profile> id = r.bodyToFlux(Profile.class)
                .flatMap(p -> this.profileService.update(id(r), p.getEmail()));
        return defaultReadResponse(id);
    }

    Mono<ServerResponse> create(ServerRequest request) {
        Flux<Profile> flux = request
                .bodyToFlux(Profile.class)
                .flatMap(toWrite -> this.profileService.create(toWrite.getEmail()));
        return defaultWriteResponse(flux);
    }

    /* We can centralize common logic in, yep! - you guessed it! — functions. This function creates
       a Mono<ServerResponse> from a Publisher<Profile> for any incoming request. Each request uses the ServerResponse
       builder object to create a response that has a Location header, a Content-Type header, and no payload.
       (You don’t need to send a payload in the response for PUT or POST, for example).
     */
    private static Mono<ServerResponse> defaultWriteResponse(Publisher<Profile> profiles) {
        return Mono
                .from(profiles)
                .flatMap(p -> ServerResponse
                        .created(URI.create("/profiles/" + p.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .build()
                );
    }

    //this method centralizes all configuration for replies to read requests (for instance, those coming from GET verbs)
    private static Mono<ServerResponse> defaultReadResponse(Publisher<Profile> profiles) {
        return ServerResponse
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(profiles, Profile.class);
    }

    private static String id(ServerRequest r) {
        return r.pathVariable("id");
    }
}
