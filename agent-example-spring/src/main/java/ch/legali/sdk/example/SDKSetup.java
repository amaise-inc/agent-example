package ch.legali.sdk.example;

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
import feign.http2client.Http2Client;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
// SDKSetup demonstrates the necessary setup of the SDK beans; it's in its own file, so that it can
// be easily copied to customer implementations.
public class SDKSetup {

  @Bean
  public ConfigService configService(SdkConfig config, MeterRegistry meterRegistry) {
    return new ConfigService(config, meterRegistry);
  }

  @Bean
  public HttpClientConfiguration httpClientConfiguration() {
    return new HttpClientConfiguration();
  }

  @Bean
  public HttpClient legaliHttpClient(
      HttpClientConfiguration httpClientConfiguration, ConfigService configService) {
    return httpClientConfiguration.legaliHttpClient(configService);
  }

  @Bean
  public Http2Client legaliFeignClient(
      HttpClientConfiguration httpClientConfiguration, HttpClient httpClient) {
    return httpClientConfiguration.legaliFeignClient(httpClient);
  }

  @Bean
  public ClientConfiguration clientConfiguration(
      Http2Client legaliFeignClient, ConfigService configService) {
    return new ClientConfiguration(legaliFeignClient, configService);
  }

  @Bean
  public LegalCaseClient legalCaseClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.legalCaseClient(authenticationRequestInterceptor);
  }

  @Bean
  public SourceFileClient sourceFileClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.sourceFileClient(authenticationRequestInterceptor);
  }

  @Bean
  public DashboardClient dashboardClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.dashboardClient(authenticationRequestInterceptor);
  }

  @Bean
  public EventClient eventClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.eventClient(authenticationRequestInterceptor);
  }

  @Bean
  public ExportClient exportClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.exportClient(authenticationRequestInterceptor);
  }

  @Bean
  public FileProxyClient fileProxyClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.fileProxyClient(authenticationRequestInterceptor);
  }

  @Bean
  public FileRedirectClient fileRedirectClient(
      ClientConfiguration clientConfiguration,
      AuthenticationRequestInterceptor authenticationRequestInterceptor) {
    return clientConfiguration.fileRedirectClient(authenticationRequestInterceptor);
  }

  @Bean
  public EventService eventService(EventClient eventClient) {
    return new EventService(eventClient);
  }

  @Bean
  public InternalFileService internalFileService(
      FileProxyClient fileProxyClient,
      FileRedirectClient fileRedirectClient,
      HttpClient httpClient,
      ConfigService configService) {
    return new InternalFileService(configService, fileProxyClient, fileRedirectClient, httpClient);
  }

  @Bean
  public FileService fileService(InternalFileService internalFileService) {
    return new FileService(internalFileService);
  }

  @Bean
  public SourceFileService sourceFileService(
      SourceFileClient sourceFileClient,
      InternalFileService internalFileService,
      ConfigService configService) {
    return new SourceFileService(sourceFileClient, internalFileService, configService);
  }

  @Bean
  public LegalCaseService legalCaseService(
      LegalCaseClient legalCaseClient, ConfigService configService) {
    return new LegalCaseService(legalCaseClient, configService);
  }

  @Bean
  public ExportService exportService(ExportClient exportClient) {
    return new ExportService(exportClient);
  }

  @Bean
  public DashboardService dashboardService(DashboardClient dashboardClient) {
    return new DashboardService(dashboardClient);
  }

  @Bean
  public HealthService healthService(
      ConfigService configService, EventClient eventClient, EventService eventService) {
    return new HealthService(configService, eventClient, eventService);
  }

  @Bean
  public Auth0AccessTokenRequestInterceptor auth0AccessTokenRequestInterceptor(
      ConfigService configService, ClientConfiguration clientConfiguration) {
    return new Auth0AccessTokenRequestInterceptor(configService, clientConfiguration.auth0Client());
  }
}
