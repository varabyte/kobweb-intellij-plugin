package com.varabyte.kobweb.intellij.notification

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

private val notificationGroup by lazy {
    NotificationGroupManager.getInstance().getNotificationGroup("Kobweb")
}

/**
 * A handle to a notification that provides some constrained operations on it.
 */
@JvmInline
value class KobwebNotificationHandle(private val notification: Notification) {
    /**
     * Manually expire the notification.
     *
     * This dismissed it and greys out any of its actions.
     */
    fun expire() {
        notification.expire()
    }
}

/**
 * A convenience class for generating notifications tagged with the Kobweb group.
 *
 * Use a [Builder] to build a notification, which you then fire by calling [Builder.notify] on it:
 *
 * ```
 * KobwebNotifier.Builder("Hello, world!").notify(project)
 * ```
 *
 * @see Notification
 */
class KobwebNotifier {
    /**
     * A builder class for creating notifications.
     *
     * You must call [notify] on the builder to actually display the notification.
     *
     * If you need to create two (or more) actions on a single notification, this is the preferred approach.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    class Builder(private val message: String) {
        constructor(title: String, message: String) : this(message) {
            title(title)
        }

        private class ActionData(val text: String, val action: () -> Unit)

        private var title: String = ""
        private var type: NotificationType = NotificationType.INFORMATION
        private var actionDataList = mutableListOf<ActionData>()

        fun title(title: String) = apply { this.title = title }

        /**
         * Register a clickable action associated with this notification.
         *
         * You can register multiple actions per notification.
         *
         * Once clicked, the action will be performed and the notification will be dismissed.
         */
        fun addAction(text: String, action: () -> Unit) =
            apply { actionDataList.add(ActionData(text, action)) }

        fun type(type: NotificationType) = apply { this.type = type }

        fun notify(project: Project): KobwebNotificationHandle {
            val notification = notificationGroup.createNotification(
                title,
                message,
                type,
            )

            actionDataList.forEach { actionData ->
                notification.addAction(object : NotificationAction(actionData.text) {
                    override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                        actionData.action()
                        notification.expire()
                    }
                })
            }

            notification.notify(project)
            return KobwebNotificationHandle(notification)
        }
    }
}
