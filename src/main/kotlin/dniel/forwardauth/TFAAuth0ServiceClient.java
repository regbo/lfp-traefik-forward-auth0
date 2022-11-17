package dniel.forwardauth;

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
import io.grpc.ManagedChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.*;
import java.util.function.BiConsumer;

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
            return this.getService().context(Empty.getDefaultInstance());
        }).retryWhen(Retry.fixedDelay(20, Duration.ofSeconds(3)).doBeforeRetry(sg -> {
            warn(sg.failure());
        })).flatMap(this::authProperties);
    }

    protected Mono<AuthProperties> authProperties(Context context) {
        var authProperties = new AuthProperties() {

            @NotNull
            @Override
            public dniel.forwardauth.domain.shared.Application findApplicationOrDefault(@Nullable String name) {
                return TFAAuth0ServiceClient.this.findApplicationOrDefault(name);
            }
        };
        CONTEXT_MAPPING.values().forEach(v -> v.accept(context, authProperties));
        return Mono.just(authProperties);
    }

    protected dniel.forwardauth.domain.shared.Application findApplicationOrDefault(@Nullable String name) {
        var requestb = ApplicationRequest.newBuilder();
        Optional.ofNullable(name).ifPresent(requestb::setName);
        var response = this.getService().application(requestb.build()).map(app -> {
            var application = new dniel.forwardauth.domain.shared.Application();
            APPLICATION_MAPPING.values().forEach(v -> v.accept(app, application));
            return application;
        }).block();
        return response;
    }

    private static void warn(Throwable exception) {
        var message = Throws.streamCauses(exception)
                .map(Throwable::getMessage)
                .filter(Objects::nonNull)
                .filter(v -> !v.isBlank())
                .findFirst()
                .orElse(null);
        LOGGER.warn(String.format("%s:%s", exception.getClass().getName(), message));
    }

    private static <I, O> Map<BeanPath<I, ?>, BiConsumer<I, O>> getMapping(Class<I> inputClassType, Class<O> outputClassType) {
        var inputBPs = BeanRef.$(inputClassType).all();
        var outputBPs = BeanRef.$(outputClassType).all();
        return Streams.of(inputBPs).mapToEntry(inputBP -> {
            var inputPaths = Streams.of(inputBP.getPath(), Utils.Strings.substringBeforeLast(inputBP.getPath(), "List"))
                    .distinct();
            return inputPaths.map(path -> {
                var bps = Streams.of(outputBPs)
                        .filter(v -> path.equals(v.getPath()))
                        .filter(v -> !v.isReadOnly())
                        .map(v -> (BeanPath<O, Object>) v)
                        .limit(2)
                        .toList();
                return bps.size() == 1 ? bps.get(0) : null;
            }).nonNull().findFirst().orElse(null);
        }).nonNullValues().mapToValue((inputBP, outputBP) -> {
            BiConsumer<I, O> setter = (input, output) -> outputBP.set(output, inputBP.get(input));
            return setter;
        }).toImmutableMap();
    }
}
