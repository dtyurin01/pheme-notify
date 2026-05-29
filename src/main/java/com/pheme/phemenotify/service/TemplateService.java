package com.pheme.phemenotify.service;


import com.pheme.phemenotify.api.exception.TemplateNotFoundException;
import com.pheme.phemenotify.persistence.entity.Channel;
import com.pheme.phemenotify.persistence.entity.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {
    private final SpringTemplateEngine templateEngine;

    public String render(EventType eventType, Channel channel, Map<String, Object> payload) {

        String templateName = resolveTemplateName(eventType, channel);

        Context context = new Context();
        context.setVariables(payload);

        try {
            return templateEngine.process(templateName, context);
        } catch (TemplateInputException e) {
            log.warn("Template not found: {}", templateName, e);
            throw new TemplateNotFoundException(templateName, e);
        }
    }


    private String resolveTemplateName(EventType eventType, Channel channel) {
        String code = eventType.getCode().toLowerCase().replace("_", "-");

        String channelDir = channel.name().toLowerCase();

        String extension = channel == Channel.EMAIL ? "html" : "txt";

        return channelDir + "/" + code + "." + extension;
    }
}
