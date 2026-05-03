package com.pheme.phemenotify.provider;

import com.pheme.phemenotify.persistence.entity.Channel;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ProviderRegistry {

    private final Map<Channel, NotificationProvider> providers;

    public ProviderRegistry(EmailProvider emailProvider, SmsProvider smsProvider, PushProvider pushProvider) {
        this.providers = Map.of(
                Channel.EMAIL, emailProvider,
                Channel.SMS, smsProvider,
                Channel.PUSH, pushProvider
        );
    }

    public NotificationProvider getProvider(Channel channel) {
        NotificationProvider provider = providers.get(channel);
        if (provider == null) {
            throw new IllegalArgumentException("No provider found for channel: " + channel);
        }
        return provider;
    }
}
