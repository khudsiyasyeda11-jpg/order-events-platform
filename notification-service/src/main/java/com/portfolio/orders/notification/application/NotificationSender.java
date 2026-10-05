package com.portfolio.orders.notification.application;

/** Port for delivering notifications (email, SMS, push...). */
public interface NotificationSender {

    void send(Notification notification);
}
