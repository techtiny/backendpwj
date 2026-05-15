package com.pwj.tracker.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WeeklyReportService {

    // Every Monday at 8:00 AM — email disabled, only backup emails are active
    @Scheduled(cron = "0 0 8 * * MON")
    public void sendWeeklyReport() {
        log.info("Weekly report email DISABLED — skipped");
    }
}
