package au.com.wallaceit.voicemail.notification;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import java.util.List;

import au.com.wallaceit.voicemail.Account;
import au.com.wallaceit.voicemail.Preferences;
import au.com.wallaceit.voicemail.activity.Accounts;
import au.com.wallaceit.voicemail.activity.FolderList;
import au.com.wallaceit.voicemail.activity.MessageList;
import au.com.wallaceit.voicemail.activity.MessageReference;
import au.com.wallaceit.voicemail.search.LocalSearch;


/**
 * This class contains methods to create the {@link PendingIntent}s for the actions of new mail notifications.
 * <p/>
 * <strong>Note:</strong>
 * We need to take special care to ensure the {@code PendingIntent}s are unique as defined in the documentation of
 * {@link PendingIntent}. Otherwise selecting a notification action might perform the action on the wrong message.
 * <p/>
 * We use the notification ID as {@code requestCode} argument to ensure each notification/action pair gets a unique
 * {@code PendingIntent}.
 */
class NotificationActionCreator {
    private final Context context;


    public NotificationActionCreator(Context context) {
        this.context = context;
    }

    public PendingIntent createViewMessagePendingIntent(MessageReference messageReference, int notificationId) {
        TaskStackBuilder stack = buildMessageViewBackStack(messageReference);
        return stack.getPendingIntent(notificationId, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createViewFolderPendingIntent(Account account, String folderName, int notificationId) {
        TaskStackBuilder stack = buildMessageListBackStack(account, folderName);
        return stack.getPendingIntent(notificationId, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createViewMessagesPendingIntent(Account account, List<MessageReference> messageReferences,
            int notificationId) {

        TaskStackBuilder stack;
        if (account.goToUnreadMessageSearch()) {
            stack = buildUnreadBackStack(account);
        } else {
            String folderName = getFolderNameOfAllMessages(messageReferences);

            if (folderName == null) {
                stack = buildFolderListBackStack(account);
            } else {
                stack = buildMessageListBackStack(account, folderName);
            }
        }

        return stack.getPendingIntent(notificationId, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createViewFolderListPendingIntent(Account account, int notificationId) {
        TaskStackBuilder stack = buildFolderListBackStack(account);
        return stack.getPendingIntent(notificationId, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createDismissAllMessagesPendingIntent(Account account, int notificationId) {
        Intent intent = NotificationActionService.createDismissAllMessagesIntent(context, account);

        return PendingIntent.getService(context, notificationId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    public PendingIntent createDismissMessagePendingIntent(Context context, MessageReference messageReference,
            int notificationId) {

        Intent intent = NotificationActionService.createDismissMessageIntent(context, messageReference);

        return PendingIntent.getService(context, notificationId, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
    }

    private TaskStackBuilder buildAccountsBackStack() {
        TaskStackBuilder stack = TaskStackBuilder.create(context);
        if (!skipAccountsInBackStack()) {
            Intent intent = new Intent(context, Accounts.class);
            intent.putExtra(Accounts.EXTRA_STARTUP, false);

            stack.addNextIntent(intent);
        }
        return stack;
    }

    private TaskStackBuilder buildFolderListBackStack(Account account) {
        TaskStackBuilder stack = buildAccountsBackStack();

        Intent intent = FolderList.actionHandleAccountIntent(context, account, false);

        stack.addNextIntent(intent);

        return stack;
    }

    private TaskStackBuilder buildUnreadBackStack(final Account account) {
        TaskStackBuilder stack = buildAccountsBackStack();

        LocalSearch search = Accounts.createUnreadSearch(context, account);
        Intent intent = MessageList.intentDisplaySearch(context, search, true, false, false);

        stack.addNextIntent(intent);

        return stack;
    }

    private TaskStackBuilder buildMessageListBackStack(Account account, String folderName) {
        TaskStackBuilder stack = skipFolderListInBackStack(account, folderName) ?
                buildAccountsBackStack() : buildFolderListBackStack(account);

        LocalSearch search = new LocalSearch(folderName);
        search.addAllowedFolder(folderName);
        search.addAccountUuid(account.getUuid());
        Intent intent = MessageList.intentDisplaySearch(context, search, false, true, true);

        stack.addNextIntent(intent);

        return stack;
    }

    private TaskStackBuilder buildMessageViewBackStack(MessageReference message) {
        Account account = Preferences.getPreferences(context).getAccount(message.getAccountUuid());
        String folderName = message.getFolderName();
        TaskStackBuilder stack = buildMessageListBackStack(account, folderName);

        Intent intent = MessageList.actionDisplayMessageIntent(context, message);

        stack.addNextIntent(intent);

        return stack;
    }

    private String getFolderNameOfAllMessages(List<MessageReference> messageReferences) {
        MessageReference firstMessage = messageReferences.get(0);
        String folderName = firstMessage.getFolderName();

        for (MessageReference messageReference : messageReferences) {
            if (!TextUtils.equals(folderName, messageReference.getFolderName())) {
                return null;
            }
        }

        return folderName;
    }

    private boolean skipFolderListInBackStack(Account account, String folderName) {
        return folderName != null && folderName.equals(account.getAutoExpandFolderName());
    }

    private boolean skipAccountsInBackStack() {
        return Preferences.getPreferences(context).getAccounts().size() == 1;
    }
}
