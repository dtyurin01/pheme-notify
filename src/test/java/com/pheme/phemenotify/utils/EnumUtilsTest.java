package com.pheme.phemenotify.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import org.junit.jupiter.api.Test;

class EnumUtilsTest {
  @Test
  void shouldReturnEnum_whenValueIsLowercase() {
    // when
    Channel result = EnumUtils.fromValue(Channel.class, "email");

    assertThat(result).isEqualTo(Channel.EMAIL);
  }

  @Test
  void shouldReturnEnum_whenValueIsMixedCase() {
    NotificationStatus result = EnumUtils.fromValue(NotificationStatus.class, "Delivered");

    assertThat(result).isEqualTo(NotificationStatus.DELIVERED);
  }

  @Test
  void shouldReturnNull_whenValueIsNull() {
    assertThat(EnumUtils.fromValue(Channel.class, null)).isNull();
  }

  @Test
  void shouldThrowException_whenValueIsUnknown() {
    assertThatThrownBy(() -> EnumUtils.fromValue(Channel.class, "UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
