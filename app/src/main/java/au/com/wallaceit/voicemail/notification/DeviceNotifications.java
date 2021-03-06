package au.com.wallaceit.voicemail.notification;


import java.util.List;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.support.v4.app.NotificationCompat.InboxStyle;

import au.com.wallaceit.voicemail.Account;
import au.com.wallaceit.voicemail.VisualVoicemail;
import au.com.wallaceit.voicemail.VisualVoicemail.NotificationHideSubject;
import au.com.wallaceit.voicemail.NotificationSetting;
import au.com.wallaceit.voicemail.R;
import au.com.wallaceit.voicemail.activity.MessageReference;

import static au.com.wallaceit.voicemail.notification.NotificationController.NOTIFICATION_LED_BLINK_SLOW;
import static au.com.wallaceit.voicemail.notification.NotificationController.platformSupportsExtendedNotifications;


class DeviceNotifications extends BaseNotifications {
    private final WearNotifications wearNotifications;
    private final LockScreenNotification lockScreenNotification;


    DeviceNotifications(NotificationController controller, NotificationActionCreator actionCreator,
            LockScreenNotification lockScreenNotification, WearNotifications wearNotifications) {
        super(controller, actionCreator);
        this.wearNotifications = wearNotifications;
        this.lockScreenNotification = lockScreenNotification;
    }

    public static DeviceNotifications newInstance(NotificationController controller,
            NotificationActionCreator actionCreator, WearNotifications wearNotifications) {
        LockScreenNotification lockScreenNotification = LockScreenNotification.newInstance(controller);
        return new DeviceNotifications(controller, actionCreator, lockScreenNotification, wearNotifications);
    }

    public Notification buildSummaryNotification(Account account, NotificationData notificationData,
            boolean silent) {
        int unreadMessageCount = notificationData.getUnreadMessageCount();

        NotificationCompat.Builder builder;
        if (isPrivacyModeActive() || !platformSupportsExtendedNotifications()) {
            builder = createSimpleSummaryNotification(account, unreadMessageCount);
        } else if (notificationData.isSingleMessageNotification()) {
            NotificationHolder holder = notificationData.getHolderForLatestNotification();
            builder = createBigTextStyleSummaryNotification(account, holder);
        } else {
            builder = createInboxStyleSummaryNotification(account, notificationData, unreadMessageCount);
        }

        if (notificationData.containsStarredMessages()) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        }

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent deletePendingIntent = actionCreator.createDismissAllMessagesPendingIntent(
                account, notificationId);
        builder.setDeleteIntent(deletePendingIntent);

        lockScreenNotification.configureLockScreenNotification(builder, notificationData);

        boolean ringAndVibrate = false;
        if (!silent && !account.isRingNotified()) {
            account.setRingNotified(true);
            ringAndVibrate = true;
        }

        NotificationSetting notificationSetting = account.getNotificationSetting();
        controller.configureNotification(
                builder,
                (notificationSetting.shouldRing()) ? notificationSetting.getRingtone() : null,
                (notificationSetting.shouldVibrate()) ? notificationSetting.getVibration() : null,
                (notificationSetting.isLed()) ? notificationSetting.getLedColor() : null,
                NOTIFICATION_LED_BLINK_SLOW,
                ringAndVibrate);

        return builder.build();
    }

    private NotificationCompat.Builder createSimpleSummaryNotification(Account account, int unreadMessageCount) {
        String accountName = controller.getAccountName(account);
        CharSequence newMailText = context.getString(R.string.notification_new_title);
        String unreadMessageCountText = context.getString(R.string.notification_new_one_account_fmt,
                unreadMessageCount, accountName);

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        PendingIntent contentIntent = actionCreator.createViewFolderListPendingIntent(account, notificationId);

        return createAndInitializeNotificationBuilder(account)
                .setNumber(unreadMessageCount)
                .setTicker(newMailText)
                .setContentTitle(unreadMessageCountText)
                .setContentText(newMailText)
                .setContentIntent(contentIntent);
    }

    private NotificationCompat.Builder createBigTextStyleSummaryNotification(Account account,
            NotificationHolder holder) {

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        Builder builder = createBigTextStyleNotification(account, holder, notificationId)
                .setGroupSummary(true);

        return builder;
    }

    private NotificationCompat.Builder createInboxStyleSummaryNotification(Account account,
            NotificationData notificationData, int unreadMessageCount) {

        NotificationHolder latestNotification = notificationData.getHolderForLatestNotification();

        int newMessagesCount = notificationData.getNewMessagesCount();
        String accountName = controller.getAccountName(account);
        String title = context.getResources().getQuantityString(R.plurals.notification_new_messages_title,
                newMessagesCount, newMessagesCount);
        String summary = (notificationData.hasAdditionalMessages()) ?
                context.getString(R.string.notification_additional_messages,
                        notificationData.getAdditionalMessagesCount(), accountName) :
                accountName;

        NotificationCompat.Builder builder = createAndInitializeNotificationBuilder(account)
                .setNumber(unreadMessageCount)
                .setTicker(latestNotification.content.sender)
                .setGroup(NOTIFICATION_GROUP_KEY)
                .setGroupSummary(true)
                .setContentTitle(title)
                .setSubText(accountName);

        NotificationCompat.InboxStyle style = createInboxStyle(builder)
                .setBigContentTitle(title)
                .setSummaryText(summary);

        for (NotificationContent content : notificationData.getContentForSummaryNotification()) {
            style.addLine(content.sender);
        }

        builder.setStyle(style);

        wearNotifications.addSummaryActions(builder, notificationData);

        int notificationId = NotificationIds.getNewMailSummaryNotificationId(account);
        List<MessageReference> messageReferences = notificationData.getAllMessageReferences();
        PendingIntent contentIntent = actionCreator.createViewMessagesPendingIntent(
                account, messageReferences, notificationId);
        builder.setContentIntent(contentIntent);

        return builder;
    }

    private boolean isPrivacyModeActive() {
        KeyguardManager keyguardService = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

        boolean privacyModeAlwaysEnabled = VisualVoicemail.getNotificationHideSubject() == NotificationHideSubject.ALWAYS;
        boolean privacyModeEnabledWhenLocked = VisualVoicemail.getNotificationHideSubject() == NotificationHideSubject.WHEN_LOCKED;
        boolean screenLocked = keyguardService.inKeyguardRestrictedInputMode();

        return privacyModeAlwaysEnabled || (privacyModeEnabledWhenLocked && screenLocked);
    }

    protected InboxStyle createInboxStyle(Builder builder) {
        return new InboxStyle(builder);
    }
}
