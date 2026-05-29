package com.pheme.phemenotify.persistence.repository;

import com.pheme.phemenotify.BaseIntegrationTest;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.EventType;
import com.pheme.phemenotify.persistence.entity.Notification;
import com.pheme.phemenotify.persistence.entity.NotificationStatus;
import com.pheme.phemenotify.persistence.entity.eventtype.OrderCompletedEventType;
import com.pheme.phemenotify.persistence.entity.eventtype.UserRegisteredEventType;
import com.pheme.phemenotify.persistence.projection.DeliveryStatsProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.pheme.phemenotify.util.TestDateUtils.daysAgo;
import static com.pheme.phemenotify.util.TestDateUtils.localDaysAgo;
import static com.pheme.phemenotify.util.TestDateUtils.today;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class NotificationRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrderCompletedEventType orderCompleted;

    @Autowired
    private UserRegisteredEventType userRegistered;

    @AfterEach
    void cleanup() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save & find notifications by ID")
    void shouldFindById_whenSaved() {
        Notification saved =
            notificationRepository.save(buildNotification("key-1", Channel.EMAIL,
                NotificationStatus.PENDING, orderCompleted));

        Optional<Notification> found =
            notificationRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo("user-123");
        assertThat(found.get().getEventType()).isEqualTo(orderCompleted);
    }

    @Test
    @DisplayName("Should return Empty, when key not found")
    void shouldReturnEmpty_whenKeyNotFound() {
        Optional<Notification> found = notificationRepository.findByIdempotencyKey("non-existent-key");
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindByIdempotencyKey_whenSaved() {
        notificationRepository.save(buildNotification("key-456", Channel.EMAIL,
            NotificationStatus.PENDING, orderCompleted));

        Optional<Notification> found =
            notificationRepository.findByIdempotencyKey("key-456");

        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo("key-456");
        assertThat(found.get().getUserId()).isEqualTo("user-123");
        assertThat(found.get().getEventType()).isEqualTo(orderCompleted);
    }


//    TEST findDeliveryStats

    @Test
    void shouldReturnEmpty_whenNoNotificationsInRange() {
        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(7), today());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldCalculateTotalsAndDeliveryRate_whenMixedStatuses() {
        // 2 DELIVERED, 1 FAILED, 1 PENDING → total=4, delivered=2, failed=1, rate=50.00
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-1");
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-2");
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.FAILED,
            "key-3");
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.PENDING,
            "key-4");

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(2), today());

        assertThat(result).hasSize(1);
        DeliveryStatsProjection deliveryStats = result.getFirst();
        assertThat(deliveryStats.getTotal()).isEqualTo(4L);
        assertThat(deliveryStats.getDelivered()).isEqualTo(2L);
        assertThat(deliveryStats.getFailed()).isEqualTo(1L);
        assertThat(deliveryStats.getDeliveryRate()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldReturnSameRateAsRollingAvg_whenOnlyOneDayExists() {
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-1");
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.FAILED,
            "key-2");

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(2), today());

        assertThat(result).hasSize(1);
        DeliveryStatsProjection deliveryStats = result.getFirst();

        assertThat(deliveryStats.getRollingWeeklyAvg()).isEqualByComparingTo(deliveryStats.getDeliveryRate());
    }

    @Test
    void shouldSlideWindow_whenMoreThanSevenDaysOfData() {
        for (int i = 8; i >= 2; i--) {
            saveWithDate(daysAgo(i), Channel.EMAIL, NotificationStatus.DELIVERED,
                "key-delivered-" + i);
        }
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.FAILED, "key-failed-1");

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(9), today());

        // Result is in day DESC - yest. is first
        DeliveryStatsProjection deliveryStatsYesterday = result.getFirst();
        assertThat(deliveryStatsYesterday.getDeliveryRate()).isEqualByComparingTo("0.00");
        // Window 7 rows: 6 * 100% + 1*0% = avg around 85.71
        assertThat(deliveryStatsYesterday.getRollingWeeklyAvg()).isEqualByComparingTo("85.71");

        // The oldest day - window with 1 row -> rolling avg = its rate = 100%
        DeliveryStatsProjection deliveryStatsOldest = result.getLast();
        assertThat(deliveryStatsOldest.getDeliveryRate()).isEqualByComparingTo("100.00");
        assertThat(deliveryStatsOldest.getRollingWeeklyAvg()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldPartitionRollingAvgByChannel() {
        for (int i = 8; i >= 2; i--) {
            saveWithDate(daysAgo(i), Channel.EMAIL, NotificationStatus.DELIVERED,
                "key-email-delivered-" + i);
            saveWithDate(daysAgo(i), Channel.SMS, NotificationStatus.FAILED,
                "key-sms-failed-" + i);
        }
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.FAILED, "key-email-failed-1");
        saveWithDate(daysAgo(1), Channel.SMS, NotificationStatus.DELIVERED, "key-sms-delivered-1");

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(9), today());

        // For EMAIL - yesterday rate is 0% - window with 6 delivered + 1 failed → avg around 85.71
        DeliveryStatsProjection emailStatsYesterday = result.stream()
            .filter(r -> r.getChannel().equals(Channel.EMAIL.name())
                && r.getDay().equals(localDaysAgo(1)))
            .findFirst().orElseThrow();
        assertThat(emailStatsYesterday.getDeliveryRate()).isEqualByComparingTo("0.00");
        assertThat(emailStatsYesterday.getRollingWeeklyAvg()).isEqualByComparingTo("85.71");

        // For SMS - yesterday rate is 100% - window with 6 failed + 1 delivered → avg around 14.29
        DeliveryStatsProjection smsStatsYesterday = result.stream()
            .filter(r -> r.getChannel().equals(Channel.SMS.name())
                && r.getDay().equals(localDaysAgo(1)))
            .findFirst().orElseThrow();
        assertThat(smsStatsYesterday.getDeliveryRate()).isEqualByComparingTo("100.00");
        assertThat(smsStatsYesterday.getRollingWeeklyAvg()).isEqualByComparingTo("14.29");

    }

    @Test
    void shouldExcludeNotificationsOutsideDateRange() {
        saveWithDate(daysAgo(8), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-before"); // before startDate
        saveWithDate(daysAgo(3), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-in");     // in range
        saveWithDate(today(), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-after");  // = endDate, excluded

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(7), today());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTotal()).isEqualTo(1L);
    }

    @Test
    void shouldGroupByEventTypeSeparately() {
        saveWithDateAndType(daysAgo(1), Channel.EMAIL,
            NotificationStatus.DELIVERED, "key-1", orderCompleted);
        saveWithDateAndType(daysAgo(1), Channel.EMAIL,
            NotificationStatus.FAILED, "key-2", userRegistered);

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(2), today());

        assertThat(result).hasSize(2);
        assertThat(result)
            .extracting(DeliveryStatsProjection::getEventType)
            .containsExactlyInAnyOrder(OrderCompletedEventType.CODE, UserRegisteredEventType.CODE);

    }

    @Test
    void shouldNotCountCancelledInDeliveredOrFailed() {
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-1");
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.CANCELLED,
            "key-2");

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(2), today());

        assertThat(result).hasSize(1);
        DeliveryStatsProjection row = result.getFirst();
        assertThat(row.getTotal()).isEqualTo(2L);
        assertThat(row.getDelivered()).isEqualTo(1L);
        assertThat(row.getFailed()).isEqualTo(0L);
        assertThat(row.getDeliveryRate()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldOrderByDayDescThenChannel() {
        saveWithDate(daysAgo(2), Channel.SMS, NotificationStatus.DELIVERED,
            "key-sms-2");
        saveWithDate(daysAgo(1), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-email-1");
        saveWithDate(daysAgo(1), Channel.SMS, NotificationStatus.DELIVERED,
            "key-sms-1");
        saveWithDate(daysAgo(2), Channel.EMAIL, NotificationStatus.DELIVERED,
            "key-email-2");

        List<DeliveryStatsProjection> result =
            notificationRepository.findDeliveryStats(daysAgo(3), today());

        assertThat(result).hasSize(4);
        // First two rows — yesterday (most recent day)
        assertThat(result.get(0).getDay()).isEqualTo(localDaysAgo(1));
        assertThat(result.get(0).getChannel()).isEqualTo(Channel.EMAIL.name());
        assertThat(result.get(1).getDay()).isEqualTo(localDaysAgo(1));
        assertThat(result.get(1).getChannel()).isEqualTo(Channel.SMS.name());
        // Last two rows — two days ago (older day)
        assertThat(result.get(2).getDay()).isEqualTo(localDaysAgo(2));
        assertThat(result.get(2).getChannel()).isEqualTo(Channel.EMAIL.name());
        assertThat(result.get(3).getDay()).isEqualTo(localDaysAgo(2));
        assertThat(result.get(3).getChannel()).isEqualTo(Channel.SMS.name());
    }

    @Test
    void shouldThrowException_whenDuplicateIdempotencyKey() {
        notificationRepository.save(
            buildNotification("duplicate-key", Channel.EMAIL, NotificationStatus.PENDING, orderCompleted));

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(
            buildNotification("duplicate-key", Channel.SMS, NotificationStatus.PENDING, orderCompleted)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    //      HELPERS
    private void saveWithDate(Instant createdAt, Channel channel,
                              NotificationStatus status, String key) {
        saveWithDateAndType(createdAt, channel, status, key, orderCompleted);
    }

    private void saveWithDateAndType(Instant createdAt, Channel channel,
                                     NotificationStatus status, String key,
                                     EventType eventType) {
        Notification saved = notificationRepository.save(
            buildNotification(key, channel, status, eventType));
        notificationRepository.updateCreatedAt(saved.getId(), createdAt);
    }

    private Notification buildNotification(String key, Channel channel,
                                           NotificationStatus status, EventType
                                               eventType) {
        return Notification.builder()
            .userId("user-123")
            .channel(channel)
            .eventType(eventType)
            .idempotencyKey(key)
            .status(status)
            .build();
    }
}
