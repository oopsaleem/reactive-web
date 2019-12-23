package com.example.demo;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.FluxSink;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Component
class ProfileCreatedEventPublisher implements
        ApplicationListener<ProfileCreatedEvent>,
        Consumer<FluxSink<ProfileCreatedEvent>> {

    private final Executor executor;
    private final BlockingQueue<ProfileCreatedEvent> queue =
            new LinkedBlockingQueue<>();

    ProfileCreatedEventPublisher(Executor executor) {
        this.executor = executor;
    }


    @Override
    public void onApplicationEvent(ProfileCreatedEvent event) {
        this.queue.offer(event);
    }

    @Override
    public void accept(FluxSink<ProfileCreatedEvent> sink) {
        this.executor.execute(() -> {
            while (true)
                try {
                    ProfileCreatedEvent event = queue.take();
                    sink.next(event);
                }
                catch (InterruptedException e) {
                    ReflectionUtils.rethrowRuntimeException(e);
                }
        });
    }
}