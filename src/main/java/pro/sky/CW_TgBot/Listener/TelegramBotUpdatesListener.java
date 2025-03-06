package pro.sky.CW_TgBot.Listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.CW_TgBot.Entity.NotificationBot;
import pro.sky.CW_TgBot.Entity.NotificationTaskEntity;
import pro.sky.CW_TgBot.Repository.NotificationTaskRepository;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {
    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private final TelegramBot telegramBot;
    private final NotificationTaskRepository notificationTaskRepository;
    private final NotificationBot notificationBot;

    public TelegramBotUpdatesListener(TelegramBot telegramBot, NotificationTaskRepository notificationTaskRepository, NotificationBot notificationBot) {
        this.telegramBot = telegramBot;
        this.notificationTaskRepository = notificationTaskRepository;
        this.notificationBot = notificationBot;
    }


    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);

            if (update.message() != null && update.message().text() != null) {
                String messageText = update.message().text();
                Long chatId = update.message().chat().id();
                if (messageText.equals("/start")) {
                    String welcomeMessage =
                            "Привет! Напиши, о чём тебе необходимо напомнить?";
                    telegramBot.execute(new SendMessage(chatId, welcomeMessage));

                    logger.info("Send welcome message to chat ID: {}", chatId);
                }
                else if (messageText.matches("(\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})(\\s+)(.+)")) {
                    doReminderMessage(messageText, chatId);

                    logger.info("Send message about remind to chat ID: {}", chatId);
                } else {
                    telegramBot.execute(new SendMessage(chatId, "Неверный формат сообщения. " +
                            "Пожалуйста, используйте формат: dd.MM.yyyy HH:mm "));
                }
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    public void doReminderMessage(String messageText, Long chatId) {
        try {
            logger.info("Method doReminderMessage start");
            String regex = "(\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})(\\s+)(.+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(messageText);

            if (matcher.matches()) {
                String dateTimeString = matcher.group(1);
                String reminderText = matcher.group(3);

                LocalDateTime reminderDateTime = LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

                NotificationTaskEntity notificationEntity = new NotificationTaskEntity();
                notificationEntity.setChatId(chatId);
                notificationEntity.setMessage(reminderText);
                notificationEntity.setDateTime(reminderDateTime);

                notificationTaskRepository.save(notificationEntity);

                telegramBot.execute(new SendMessage(chatId, "Напоминание успешно создано!"));

                logger.info("Remind is in dataBase for chat ID: {}", chatId);
            }
        } catch (Exception e) {
            logger.error("Exception for message: " + messageText);

            telegramBot.execute(new SendMessage(chatId, "Произошла ошибка при создании напоминания. " +
                    "Проверьте формат сообщения."));
        }
    }

    @Scheduled(cron = "0 0/1 * * * *")
    public void sendNotifications() {

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        List<NotificationTaskEntity> tasksToNotify = notificationTaskRepository.findNotificationTaskEntityByDateTime(now);

        tasksToNotify.forEach(task -> {
            Long chatId = task.getChatId();
            String message = task.getMessage();

            try {
                notificationBot.sendNotification(chatId, message);

                notificationTaskRepository.delete(task);

                logger.info("Notification sent to chat ID {}: {}", chatId, message);
            } catch (Exception e) {
                logger.error("Failed to send notification to chat ID {}: {}", chatId, e.getMessage(), e);
            }
        });
    }
}
