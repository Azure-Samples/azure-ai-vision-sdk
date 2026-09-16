package com.azure.ai.vision.face.sample.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import com.azure.ai.vision.face.deviceattestation.store.ClusterStore;
import com.azure.ai.vision.face.sample.config.AppSettings;
import com.azure.ai.vision.face.sample.config.StorageConfig;
import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;

import io.lettuce.authx.TokenBasedRedisCredentialsProvider;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisCredentials;
import reactor.core.publisher.Mono;
import redis.clients.authentication.core.Token;
import redis.clients.authentication.core.TokenAuthConfig;
import redis.clients.authentication.core.TokenManagerConfig;
import redis.clients.authentication.entraid.AzureIdentityProvider;
import redis.clients.authentication.entraid.AzureTokenAuthConfigBuilder;

class RedisSupportTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void azureConnectionsUseStreamingCredentialsAndReauthentication() {
        var credentials = mock(TokenBasedRedisCredentialsProvider.class);
        var config = new RedisStandaloneConfiguration("example.redis.azure.net", 10000);
        LettuceConnectionFactory factory = RedisSupport.createConnectionFactory(config, credentials);
        try {
            var client = factory.getClientConfiguration();
            assertThat(client.isUseSsl()).isTrue();
            assertThat(config.getPassword().isPresent()).isFalse();
                assertThat(client.getClientOptions().orElseThrow().getReauthenticateBehaviour())
                    .isEqualTo(ClientOptions.ReauthenticateBehavior.ON_NEW_CREDENTIALS);
            assertThat(client.getRedisCredentialsProviderFactory().orElseThrow().createCredentialsProvider(config))
                    .isSameAs(credentials);
        } finally {
            factory.destroy();
        }
        verify(credentials).close();
    }

    @Test
    void localConnectionsDoNotAcquireEntraCredentials() {
        var config = new RedisStandaloneConfiguration("127.0.0.1", 6379);
        LettuceConnectionFactory factory = RedisSupport.createConnectionFactory(config, null);
        try {
            assertThat(factory.getClientConfiguration().isUseSsl()).isFalse();
            assertThat(factory.getClientConfiguration().getRedisCredentialsProviderFactory()).isEmpty();
            assertThat(config.getPassword().isPresent()).isFalse();
        } finally {
            factory.destroy();
        }
    }

    @Test
        void azureProviderPreservesExpiryAndRequestsTheRedisScope() {
        AccessToken accessToken = accessToken("test-token", OffsetDateTime.now().plusHours(1).withNano(0));
        var credential = mock(DefaultAzureCredential.class);
        when(credential.getToken(any())).thenAnswer(invocation -> {
            TokenRequestContext context = invocation.getArgument(0);
            assertThat(context.getScopes()).containsExactly("https://redis.azure.com/.default");
            return Mono.just(accessToken);
        });
        var provider = RedisSupport.createTokenAuthConfig(credential).getIdentityProviderConfig().getProvider();
        assertThat(provider).isInstanceOf(AzureIdentityProvider.class);
        Token token = provider.requestToken();
        assertThat(token.getUser()).isEqualTo("object-id");
        assertThat(token.getValue()).isEqualTo(accessToken.getToken());
        assertThat(token.getExpiresAt()).isEqualTo(accessToken.getExpiresAt().toInstant().toEpochMilli());
        assertThat(token.isExpired()).isFalse();
    }

    @Test
        void azureProviderRejectsMalformedTokens() {
        var credential = mock(DefaultAzureCredential.class);
        when(credential.getToken(any())).thenReturn(
            Mono.just(new AccessToken("malformed", OffsetDateTime.now().plusHours(1))));
        var provider = RedisSupport.createTokenAuthConfig(credential).getIdentityProviderConfig().getProvider();
        assertThatThrownBy(provider::requestToken).isInstanceOf(JWTDecodeException.class);
    }

    @Test
        void usesLibraryRenewalTimingWithPersistentRetries() {
        var config = RedisSupport.createTokenAuthConfig(mock(DefaultAzureCredential.class)).getTokenManagerConfig();
        assertThat(config.getExpirationRefreshRatio())
            .isEqualTo(AzureTokenAuthConfigBuilder.DEFAULT_EXPIRATION_REFRESH_RATIO);
        assertThat(config.getLowerRefreshBoundMillis())
            .isEqualTo(AzureTokenAuthConfigBuilder.DEFAULT_LOWER_REFRESH_BOUND_MILLIS);
        assertThat(config.getTokenRequestExecTimeoutInMs()).isEqualTo(30_000);
        assertThat(config.getRetryPolicy().getMaxAttempts()).isEqualTo(Integer.MAX_VALUE);
        assertThat(config.getRetryPolicy().getdelayInMs()).isEqualTo(30_000);
    }

    @Test
    void publishesRotatedCredentialsAndRecoversFromTransientFailure() throws Exception {
        AccessToken first = accessToken("first", OffsetDateTime.now().plusSeconds(10));
        AccessToken second = accessToken("second", OffsetDateTime.now().plusHours(1));
        var refreshedToken = new CompletableFuture<AccessToken>();
        var requests = new AtomicInteger();
        var credential = mock(DefaultAzureCredential.class);
        when(credential.getToken(any())).thenAnswer(invocation -> switch (requests.incrementAndGet()) {
            case 1 -> Mono.just(first);
            case 2 -> Mono.error(new IllegalStateException("Temporary identity outage"));
            default -> Mono.fromFuture(refreshedToken);
        });
        TokenAuthConfig authConfig = RedisSupport.createTokenAuthConfig(credential);
        var renewalConfig = authConfig.getTokenManagerConfig();
        var fastRetryConfig = new TokenManagerConfig(renewalConfig.getExpirationRefreshRatio(),
            renewalConfig.getLowerRefreshBoundMillis(), renewalConfig.getTokenRequestExecTimeoutInMs(),
            new TokenManagerConfig.RetryPolicy(renewalConfig.getRetryPolicy().getMaxAttempts(), 20));
        var updates = new LinkedBlockingQueue<RedisCredentials>();
        var closed = new CountDownLatch(1);
        var failure = new CompletableFuture<Throwable>();
        try (var provider = TokenBasedRedisCredentialsProvider.create(
            new TokenAuthConfig(fastRetryConfig, authConfig.getIdentityProviderConfig()))) {
            assertThat(provider.supportsStreaming()).isTrue();
            var subscription = provider.credentials().subscribe(updates::add, failure::complete, closed::countDown);
            try {
                RedisCredentials initial = updates.poll(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(initial).isNotNull();
                assertThat(initial.getUsername()).isEqualTo("object-id");
                assertThat(new String(initial.getPassword())).isEqualTo(first.getToken());
                refreshedToken.complete(second);
                RedisCredentials rotated = updates.poll(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                assertThat(rotated).isNotNull();
                assertThat(new String(rotated.getPassword())).isEqualTo(second.getToken());
                assertThat(provider.resolveCredentials().block(WAIT_TIMEOUT)).isSameAs(rotated);
                assertThat(requests.get()).isGreaterThanOrEqualTo(3);
                assertThat(first.isExpired()).isFalse();
                provider.close();
                assertThat(closed.await(WAIT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
                assertThat(failure.isDone()).isFalse();
            } finally {
                subscription.dispose();
            }
        }
    }

    @Test
    void springClosesTheRedisFactoryUsedByBothStores() {
        var factory = mock(LettuceConnectionFactory.class);
        try (var support = mockStatic(RedisSupport.class)) {
            support.when(() -> RedisSupport.tryCreate(any(AppSettings.class), any(Logger.class))).thenReturn(factory);
            try (var context = new AnnotationConfigApplicationContext()) {
                context.registerBean(AppSettings.class, AppSettings::new);
                context.register(StorageConfig.class);
                context.refresh();
                assertThat(context.getBean(ClusterStore.class)).isInstanceOf(RedisClusterStore.class);
                assertThat(context.getBean(AppSessionStore.class)).isInstanceOf(RedisAppSessionStore.class);
                assertThat(context.getBean(LettuceConnectionFactory.class)).isSameAs(factory);
            }
            verify(factory).destroy();
        }
    }

    private static AccessToken accessToken(String tokenId, OffsetDateTime expiresAt) {
        String token = JWT.create().withClaim("oid", "object-id").withJWTId(tokenId)
                .withExpiresAt(expiresAt.toInstant()).sign(Algorithm.none());
        return new AccessToken(token, expiresAt);
    }
}