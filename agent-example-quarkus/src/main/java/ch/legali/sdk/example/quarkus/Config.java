package ch.legali.sdk.example.quarkus;

import ch.legali.sdk.SdkConfig;
import ch.legali.sdk.internal.Auth0AccessTokenRequestInterceptor;
import ch.legali.sdk.internal.AuthenticationRequestInterceptor;
import ch.legali.sdk.internal.HealthService;
import ch.legali.sdk.internal.InternalFileService;
import ch.legali.sdk.internal.client.DashboardClient;
import ch.legali.sdk.internal.client.EventClient;
import ch.legali.sdk.internal.client.ExportClient;
import ch.legali.sdk.internal.client.FileProxyClient;
import ch.legali.sdk.internal.client.FileRedirectClient;
import ch.legali.sdk.internal.client.LegalCaseClient;
import ch.legali.sdk.internal.client.SourceFileClient;
import ch.legali.sdk.internal.config.ClientConfiguration;
import ch.legali.sdk.internal.config.ConfigService;
import ch.legali.sdk.internal.config.HttpClientConfiguration;
import ch.legali.sdk.services.DashboardService;
import ch.legali.sdk.services.EventService;
import ch.legali.sdk.services.ExportService;
import ch.legali.sdk.services.FileService;
import ch.legali.sdk.services.LegalCaseService;
import ch.legali.sdk.services.SourceFileService;
import feign.Logger;
import feign.http2client.Http2Client;
import io.micrometer.core.instrument.MeterRegistry;
import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class Config {

  @ConfigMapping(prefix = "legali")
  interface Mapping {

    String apiUrl();

    String clientId();

    String clientSecret();

    Optional<String> authUrl();

    Optional<Logger.Level> feignLogLevel();

    Optional<Integer> requestConnectionTimeoutSeconds();

    Optional<Integer> requestReadTimeoutSeconds();

    Optional<Integer> maxFailedHeartbeats();

    Optional<String> httpProxyHost();

    Optional<Integer> httpProxyPort();

    Map<String, String> defaultMetadata();

    Optional<SdkConfig.FileServiceType> fileService();

    Optional<Integer> maxConnectionRetries();

    Map<String, UUID> tenants();
  }

  @Produces
  @Singleton
  SdkConfig toSDKConfig(Mapping mapping) {
    SdkConfig config = new SdkConfig();
    config.setApiUrl(mapping.apiUrl());
    config.setClientId(mapping.clientId());
    config.setClientSecret(mapping.clientSecret());
    mapping.authUrl().ifPresent(config::setAuthUrl);
    mapping.feignLogLevel().ifPresent(config::setFeignLogLevel);
    mapping.requestConnectionTimeoutSeconds().ifPresent(config::setRequestConnectionTimeoutSeconds);
    mapping.requestReadTimeoutSeconds().ifPresent(config::setRequestReadTimeoutSeconds);
    mapping.maxFailedHeartbeats().ifPresent(config::setMaxFailedHeartbeats);
    mapping.httpProxyHost().ifPresent(config::setHttpProxyHost);
    mapping.httpProxyPort().ifPresent(config::setHttpProxyPort);
    config.setDefaultMetadata(mapping.defaultMetadata());
    mapping.fileService().ifPresent(config::setFileService);
    mapping.maxConnectionRetries().ifPresent(config::setMaxConnectionRetries);
    return config;
  }

  @Produces
  @Singleton
  public ConfigService configService(SdkConfig sdkConfig, MeterRegistry meterRegistry) {
    return new ConfigService(sdkConfig, meterRegistry);
  }

  @Produces
  @Singleton
  public HttpClientConfiguration httpClientConfiguration() {
    return new HttpClientConfiguration();
  }

  @Produces
  @Singleton
  public HttpClient legaliHttpClient(
      HttpClientConfiguration httpClientConfiguration, ConfigService configService) {
    return httpClientConfiguration.legaliHttpClient(configService);
  }

  @Produces
  @Singleton
  public Http2Client legaliFeignClient(
      HttpClientConfiguration httpClientConfiguration, HttpClient httpClient) {
    return httpClientConfiguration.legaliFeignClient(httpClient);
  }

  @Produces
  @Singleton
  public ClientConfiguration clientConfiguration(
      Http2Client legaliFeignClient, ConfigService configService) {
    return new ClientConfiguration(legaliFeignClient, configService);
  }

  @Produces
  @Singleton
  public LegalCaseClient legalCaseClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.legalCaseClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public SourceFileClient sourceFileClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.sourceFileClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public DashboardClient dashboardClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.dashboardClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public EventClient eventClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.eventClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public ExportClient exportClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.exportClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public FileProxyClient fileProxyClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.fileProxyClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public FileRedirectClient fileRedirectClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.fileRedirectClient(authenticationRequestInterceptor);
  }

  @Produces
  @Singleton
  public EventService eventService(EventClient eventClient) {
    return new EventService(eventClient);
  }

  @Produces
  @Singleton
  public InternalFileService internalFileService(
      FileProxyClient fileProxyClient,
      FileRedirectClient fileRedirectClient,
      HttpClient httpClient,
      ConfigService configService) {
    return new InternalFileService(configService, fileProxyClient, fileRedirectClient, httpClient);
  }

  @Produces
  @Singleton
  public FileService fileService(InternalFileService internalFileService) {
    return new FileService(internalFileService);
  }

  @Produces
  @Singleton
  public SourceFileService sourceFileService(
      SourceFileClient sourceFileClient,
      InternalFileService internalFileService,
      ConfigService configService) {
    return new SourceFileService(sourceFileClient, internalFileService, configService);
  }

  @Produces
  @Singleton
  public LegalCaseService legalCaseService(
      LegalCaseClient legalCaseClient, ConfigService configService) {
    return new LegalCaseService(legalCaseClient, configService);
  }

  @Produces
  @Singleton
  public DashboardService dashboardService(DashboardClient legalCaseClient) {
    return new DashboardService(legalCaseClient);
  }

  @Produces
  @Singleton
  public ExportService exportService(ExportClient exportClient) {
    return new ExportService(exportClient);
  }

  @Produces
  @Singleton
  public HealthService healthService(
      ConfigService configService, EventClient eventClient, EventService eventService) {
    return new HealthService(configService, eventClient, eventService);
  }

  @Produces
  @Singleton
  public AuthenticationRequestInterceptor auth0AccessTokenRequestInterceptor(
      ConfigService configService, ClientConfiguration clientConfiguration) {
    return new Auth0AccessTokenRequestInterceptor(configService, clientConfiguration.auth0Client());
  }
}
