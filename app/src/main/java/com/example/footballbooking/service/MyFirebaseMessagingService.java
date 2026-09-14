package com.example.footballbooking.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.footballbooking.R;
import com.example.footballbooking.ui.customer.CustomerMainActivity;
import com.example.footballbooking.utils.Constants;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

/**
 * MyFirebaseMessagingService — Xử lý FCM push notifications.
 *
 * KỊCH BẢN NHẬN NOTIFICATION:
 * 1. Admin duyệt booking → FCM gửi "booking_approved"
 * 2. Admin từ chối booking → FCM gửi "booking_rejected"
 * 3. 30 phút trước giờ đá → FCM gửi "reminder"
 *
 * PAYLOAD FCM (data message):
 * {
 *   "type": "booking_approved",
 *   "bookingId": "xxx",
 *   "pitchName": "Sân Mỹ Đình",
 *   "time": "18:00"
 * }
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID   = "football_booking_channel";
    private static final String CHANNEL_NAME = "Đặt sân bóng";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM Token: " + token);
        // Lưu token vào Firestore để server có thể gửi notification
        saveTokenToFirestore(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message from: " + remoteMessage.getFrom());

        // Ưu tiên xử lý Data payload (notification + data)
        if (!remoteMessage.getData().isEmpty()) {
            handleDataMessage(remoteMessage.getData());
        }

        // Nếu app foreground → notification không auto-show, phải tự show
        if (remoteMessage.getNotification() != null) {
            showNotification(
                    remoteMessage.getNotification().getTitle(),
                    remoteMessage.getNotification().getBody(),
                    remoteMessage.getData()
            );
        }
    }

    // ============================================================
    // PRIVATE HANDLERS
    // ============================================================

    private void handleDataMessage(Map<String, String> data) {
        String type       = data.get("type");
        String bookingId  = data.get("bookingId");
        String pitchName  = data.getOrDefault("pitchName", "sân bóng");
        String time       = data.getOrDefault("time", "");

        String title, body;

        if (Constants.NOTIF_BOOKING_APPROVED.equals(type)) {
            title = "✅ Đơn đặt sân được duyệt!";
            body  = "Sân " + pitchName + " lúc " + time + " đã được xác nhận. Sẵn sàng cống hiến!";
        } else if (Constants.NOTIF_BOOKING_REJECTED.equals(type)) {
            title = "❌ Đơn đặt sân bị từ chối";
            body  = "Đơn đặt " + pitchName + " đã bị từ chối. Xem lý do trong lịch sử đặt sân.";
        } else if (Constants.NOTIF_REMINDER.equals(type)) {
            title = "⏰ Nhắc nhở: Sắp đến giờ đá bóng!";
            body  = "Bạn có trận đấu tại " + pitchName + " lúc " + time + " (30 phút nữa)";
        } else {
            title = "Football Booking";
            body  = data.getOrDefault("message", "Bạn có thông báo mới");
        }

        showNotification(title, body, data);

        // Lưu notification vào Firestore để hiển thị trong app
        saveNotificationToFirestore(type, title, body, bookingId);
    }

    private void showNotification(String title, String body, Map<String, String> data) {
        // Intent mở app khi tap notification
        Intent intent = new Intent(this, CustomerMainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (data != null && data.containsKey("bookingId")) {
            intent.putExtra(Constants.EXTRA_BOOKING_ID, data.get("bookingId"));
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body)) // Expand khi dài
                .setAutoCancel(true)      // Tự xóa khi tap
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // Heads-up notification
                .setContentIntent(pendingIntent);

        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Android 8+ phải tạo NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Thông báo đặt sân và nhắc lịch");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /** Lưu FCM token vào Firestore cho user hiện tại */
    private void saveTokenToFirestore(String token) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> update = new HashMap<>();
        update.put("fcmToken", token);

        FirebaseFirestore.getInstance()
                .collection(Constants.COL_USERS)
                .document(user.getUid())
                .update(update)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save FCM token", e));
    }

    /** Lưu notification vào Firestore để user xem lại */
    private void saveNotificationToFirestore(String type, String title, String body,
                                              String bookingId) {
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        Map<String, Object> notif = new HashMap<>();
        notif.put("type", type != null ? type : "general");
        notif.put("title", title);
        notif.put("body", body);
        notif.put("bookingId", bookingId != null ? bookingId : "");
        notif.put("isRead", false);
        notif.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection(Constants.COL_USERS)
                .document(user.getUid())
                .collection(Constants.COL_NOTIFICATIONS)
                .add(notif)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to save notification", e));
    }
}
