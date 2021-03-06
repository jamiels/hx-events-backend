package com.fidecent.fbn.hx.configs;

import com.fidecent.fbn.hx.dto.MailSetting;
import com.fidecent.fbn.hx.service.MailService;
import com.fidecent.fbn.hx.service.SettingsService;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Instant;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Log4j2
@Configuration
@EnableScheduling
public class MailScheduler implements SchedulingConfigurer {

    private final MailService mailService;
    private final SettingsService settingsService;

    public MailScheduler(MailService mailService, SettingsService settingsService) {
        this.mailService = mailService;
        this.settingsService = settingsService;
    }

    @Bean/*(destroyMethod = "shutdown")*/
    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(100);
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
        taskRegistrar.addTriggerTask(mailService::sendQueuedMails,
                triggerContext -> {
                    MailSetting settings = settingsService.getMailQueueSettings();
                    Date lastActualExecutionTime = triggerContext.lastActualExecutionTime();
                    Calendar nextExecutionTime = new GregorianCalendar();
                    nextExecutionTime.setTime(lastActualExecutionTime != null ? lastActualExecutionTime : new Date());
                    nextExecutionTime.add(Calendar.SECOND, settings.getInterval());
                    Date time = nextExecutionTime.getTime();
                    log.debug("Next Mail queue run scheduled at {}", time);
                    return time;
                }
        );
    }
}