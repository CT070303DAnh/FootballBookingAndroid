package com.example.footballbooking;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.footballbooking.ui.customer.payment.PaymentActivity;
import org.junit.Test;
import static org.junit.Assert.*;

public class PaymentEntryTest {
    @Test public void missingBookingShowsErrorWithoutOpeningGateway() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), PaymentActivity.class);
        try (ActivityScenario<PaymentActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.layout_payment_result).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.web_view_payment).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.btn_payment_resume).getVisibility());
                assertTrue(((TextView)activity.findViewById(R.id.tv_payment_result_message)).getText().toString().contains("Không tìm thấy"));
            });
        }
    }

    @Test public void forgedSuccessDeepLinkNeverDisplaysPaid() {
        Intent intent = new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(), PaymentActivity.class);
        intent.setData(Uri.parse("vnpay://payment_result?vnp_ResponseCode=00&payment_ref=invalid"));
        try (ActivityScenario<PaymentActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.layout_payment_result).getVisibility());
                String message = ((TextView)activity.findViewById(R.id.tv_payment_result_message)).getText().toString();
                assertFalse(message.contains("Thanh toán đã được xác nhận"));
                assertEquals(View.GONE, activity.findViewById(R.id.btn_payment_resume).getVisibility());
            });
        }
    }
}
