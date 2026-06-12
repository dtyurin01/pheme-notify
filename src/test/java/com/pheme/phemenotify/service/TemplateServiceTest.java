package com.pheme.phemenotify.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pheme.phemenotify.api.exception.TemplateNotFoundException;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import com.pheme.phemenotify.persistence.entity.eventtype.PaymentFailedEventType;
import com.pheme.phemenotify.persistence.entity.eventtype.UserRegisteredEventType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.context.IContext;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.spring6.SpringTemplateEngine;

@ExtendWith(MockitoExtension.class)
public class TemplateServiceTest {
  @Mock private SpringTemplateEngine templateEngine;

  @InjectMocks private TemplateService templateService;

  @Test
  void shouldReturnRenderedContent_whenTemplateExists() {
    when(templateEngine.process(any(String.class), any(IContext.class)))
        .thenReturn("<h1>Order completed</h1>");

    String result =
        templateService.render(
            new OrderCompletedEventType(), Channel.EMAIL, Map.of("orderId", "123"));

    assertThat(result).isEqualTo("<h1>Order completed</h1>");
  }

  @Test
  void shouldResolveCorrectTemplateName_whenEmailChannel() {
    when(templateEngine.process(any(String.class), any(IContext.class)))
        .thenReturn("<h1>Order completed</h1>");

    templateService.render(new OrderCompletedEventType(), Channel.EMAIL, Map.of());

    verify(templateEngine).process(eq("email/order-completed.html"), any(IContext.class));
  }

  @Test
  void shouldResolveCorrectTemplateName_whenSmsChannel() {
    when(templateEngine.process(any(String.class), any(IContext.class))).thenReturn("rendered");

    templateService.render(new UserRegisteredEventType(), Channel.SMS, Map.of());

    verify(templateEngine).process(eq("sms/user-registered.txt"), any(IContext.class));
  }

  @Test
  void shouldResolveCorrectTemplateName_whenPushChannel() {
    when(templateEngine.process(any(String.class), any(IContext.class))).thenReturn("rendered");

    templateService.render(new OrderCompletedEventType(), Channel.PUSH, Map.of());

    verify(templateEngine).process(eq("push/order-completed.txt"), any(IContext.class));
  }

  @Test
  void shouldPassPayloadAsVariables_whenRendering() {
    ArgumentCaptor<IContext> contextCaptor = ArgumentCaptor.forClass(IContext.class);
    when(templateEngine.process(any(String.class), contextCaptor.capture())).thenReturn("rendered");

    Map<String, Object> payload = Map.of("orderId", "123", "amount", "500");

    templateService.render(new OrderCompletedEventType(), Channel.EMAIL, payload);

    IContext context = contextCaptor.getValue();

    assertThat(context.getVariable("orderId")).isEqualTo("123");
    assertThat(context.getVariable("amount")).isEqualTo("500");
  }

  @Test
  void shouldResolveCorrectTemplateName_whenEventTypeHasMultipleWords() {
    when(templateEngine.process(any(String.class), any(IContext.class))).thenReturn("rendered");

    templateService.render(new PaymentFailedEventType(), Channel.EMAIL, Map.of());

    verify(templateEngine).process(eq("email/payment-failed.html"), any(IContext.class));
  }

  @Test
  void shouldThrowTemplateNotFoundException_whenTemplateNotFound() {
    TemplateInputException exception =
        new TemplateInputException("not found", new RuntimeException());
    when(templateEngine.process(any(String.class), any(IContext.class))).thenThrow(exception);

    assertThatThrownBy(
            () -> templateService.render(new OrderCompletedEventType(), Channel.EMAIL, Map.of()))
        .isInstanceOf(TemplateNotFoundException.class)
        .hasMessageContaining("email/order-completed.html")
        .hasCause(exception);
  }

  @Test
  void shouldNotWrap_whenNonTemplateInoutExceptionThrown() {
    RuntimeException exception = new RuntimeException("unexpected engine failure");
    when(templateEngine.process(any(String.class), any(IContext.class))).thenThrow(exception);

    assertThatThrownBy(
            () -> templateService.render(new OrderCompletedEventType(), Channel.EMAIL, Map.of()))
        .isInstanceOf(RuntimeException.class)
        .isNotInstanceOf(TemplateNotFoundException.class)
        .hasMessage("unexpected engine failure");
  }
}
