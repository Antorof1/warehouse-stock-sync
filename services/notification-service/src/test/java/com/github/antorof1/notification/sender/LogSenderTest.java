package com.github.antorof1.notification.sender;

import com.github.antorof1.notification.dispatch.Notification;
import com.github.antorof1.notification.dispatch.NotificationSeverity;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LogSenderTest {
    private final LogSender sender = new LogSender();

    @ParameterizedTest
    @CsvSource({"INFO, INFO", "WARNING, WARN", "CRITICAL, ERROR"})
    void logsTheNotificationAtTheLevelOfItsSeverity(NotificationSeverity severity, String level, CapturedOutput output) {
        sender.send(new Notification("Label-" + severity, "body text", severity));

        assertThat(output.getAll())
            .containsPattern(level + ".*" + LogSender.class.getSimpleName() + ".*Label-" + severity + " body text");
    }
}
