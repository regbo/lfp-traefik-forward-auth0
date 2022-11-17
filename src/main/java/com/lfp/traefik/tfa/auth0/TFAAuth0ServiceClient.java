package com.lfp.traefik.tfa.auth0;

import com.github.throwable.beanref.BeanPath;
import com.github.throwable.beanref.BeanRef;
import com.google.protobuf.Empty;
import com.lfp.joe.core.function.Throws;
import com.lfp.joe.core.log.Logging;
import com.lfp.joe.core.properties.Configs;
import com.lfp.joe.grpc.GRPCClient;
import com.lfp.joe.stream.Streams;
import com.lfp.joe.utils.Utils;
import com.lfp.traefik.impl.grpc.tfa.auth0.Application;
import com.lfp.traefik.impl.grpc.tfa.auth0.ApplicationRequest;
import com.lfp.traefik.impl.grpc.tfa.auth0.Context;
import com.lfp.traefik.impl.grpc.tfa.auth0.ReactorTFAAuth0ServiceGrpc;
import com.lfp.traefik.impl.grpc.tfa.config.TFAAuthServiceConfig;
import dniel.forwardauth.AuthProperties;
import io.grpc.ManagedChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

@Component
public class TFAAuth0ServiceClient extends GRPCClient<ReactorTFAAuth0ServiceGrpc.ReactorTFAAuth0ServiceStub> {

    private static final Logger LOGGER = Logging.logger();
    private static final Map<BeanPath<Context, ?>, BiConsumer<Context, AuthProperties>> CONTEXT_MAPPING = getMapping(Context.class, AuthProperties.class);
    private static final Map<BeanPath<Application, ?>, BiConsumer<Application, dniel.forwardauth.domain.shared.Application>> APPLICATION_MAPPING = getMapping(Application.class, dniel.forwardauth.domain.shared.Application.class);

    public TFAAuth0ServiceClient() {
        this(Configs.get(TFAAuthServiceConfig.class));
    }

    protected TFAAuth0ServiceClient(TFAAuthServiceConfig config) {
        super(config.address(), null, cb -> cb.usePlaintext());
    }

    @Override
    protected ReactorTFAAuth0ServiceGrpc.ReactorTFAAuth0ServiceStub createService(ManagedChannel channel) {
        return ReactorTFAAuth0ServiceGrpc.newReactorStub(channel);
    }

    public Mono<AuthProperties> authProperties() {
        return Mono.defer(() -> {
                    return this.getService()
                            .context(Empty.getDefaultInstance());
                })
                .retryWhen(Retry.any()
                        .timeout(Duration.ofMinutes(1))
                        .fixedBackoff(Duration.ofSeconds(3))
                        .doOnRetry(ctx -> {
                            warn(ctx.exception());
                        }))
                .flatMap(this::authProperties);
    }

    protected Mono<AuthProperties> authProperties(Context context) {
        var authProperties = new AuthProperties() {

            @NotNull
            @Override
            public dniel.forwardauth.domain.shared.Application findApplicationOrDefault(@Nullable String name) {
                return TFAAuth0ServiceClient.this.findApplicationOrDefault(name);
            }
        };
        CONTEXT_MAPPING.values()
                .forEach(v -> v.accept(context, authProperties));
        return Mono.just(authProperties);
    }

    protected dniel.forwardauth.domain.shared.Application findApplicationOrDefault(@Nullable String name) {
        var requestb = ApplicationRequest.newBuilder();
        Optional.ofNullable(name)
                .ifPresent(requestb::setName);
        return this.getService()
                .application(requestb.build())
                .map(app -> {
                    var application = new dniel.forwardauth.domain.shared.Application();
                    APPLICATION_MAPPING.values()
                            .forEach(v -> v.accept(app, application));
                    return application;
                }).block();

    }

    private static void warn(Throwable exception) {
        var message = Throws.streamCauses(exception)
                .map(Throwable::getMessage)
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElse(null);
        LOGGER.warn(String.format("%s:%s", exception.getClass()
                .getName(), message));
    }

    private static <I, O> Map<BeanPath<I, ?>, BiConsumer<I, O>> getMapping(Class<I> inputClassType, Class<O> outputClassType) {
        var inputBPs = BeanRef.$(inputClassType)
                .all();
        var outputBPs = BeanRef.$(outputClassType)
                .all();
        Map<BeanPath<I, ?>, BiConsumer<I, O>> mapping = new LinkedHashMap<>();
        for (var inputBP : inputBPs) {
            BiConsumer<I, O> setter = null;
            for (var listMapping : List.of(false, true)) {
                if (setter != null) break;
                if (listMapping && !List.class.isAssignableFrom(inputBP.getType())) continue;
                String inputBPPath;
                if (!listMapping) inputBPPath = inputBP.getPath();
                else {
                    inputBPPath = Utils.Strings.substringBeforeLast(inputBP.getPath(), "List");
                    if (inputBP.getPath()
                            .equals(inputBPPath)) continue;
                }
                var outputBP = Streams.of(outputBPs)
                        .filter(v -> inputBPPath.equals(v.getPath()))
                        .filter(v -> listMapping || !v.isReadOnly())
                        .map(v -> (BeanPath<O, Object>) v)
                        .limit(2)
                        .chain(s -> Stream.of(s.toList()))
                        .filter(v -> v.size() == 1)
                        .map(v -> v.get(0))
                        .findFirst()
                        .orElse(null);
                if (outputBP == null) continue;
                setter = (input, output) -> {
                    if (!listMapping) outputBP.set(output, inputBP.get(input));
                    else {
                        var inputList = (List) inputBP.get(input);
                        var outputList = (List) outputBP.get(output);
                        outputList.clear();
                        outputList.addAll(inputList);
                    }
                };
            }
            if (setter != null) mapping.put(inputBP, setter);
        }
        return Collections.unmodifiableMap(mapping);
    }
}
