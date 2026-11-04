package com.smartdeploy.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Active l'exécution asynchrone des notifications (pool de tâches de Spring Boot). */
@Configuration
@EnableAsync
public class NotificationAsyncConfig {
}
