package com.myrtle.middleware.services.notifications;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PushNotificationService {

    public String sendPush(String fcmToken, String title, String body) throws FirebaseMessagingException {
        String tokenPreview = fcmToken == null || fcmToken.length() < 8
                ? "****"
                : fcmToken.substring(0, 6) + "...";
        log.debug("Sending push notification | tokenPreview={} | title={}", tokenPreview, title);

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .putData("click_action", "FLUTTER_NOTIFICATION_CLICK")
                .build();

        try {
            String messageId = FirebaseMessaging.getInstance().send(message);
            log.info("Push notification sent | tokenPreview={} | messageId={}", tokenPreview, messageId);
            return messageId;
        } catch (FirebaseMessagingException ex) {
            log.error("Push notification failed | tokenPreview={} | errorCode={} | reason={}",
                    tokenPreview, ex.getMessagingErrorCode(), ex.getMessage());
            throw ex;
        }
    }
}
