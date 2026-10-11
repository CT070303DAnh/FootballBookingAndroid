package com.example.footballbooking;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelStore;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.footballbooking.ui.customer.booking.BookingViewModel;
import com.example.footballbooking.data.model.TimeSlot;
import com.example.footballbooking.data.model.Service;
import com.example.footballbooking.utils.Resource;
import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class BookingSelectionTest {
    @Test public void blockedConfirmationReportsErrorForEveryPaymentMethod() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            BookingViewModel vm = new BookingViewModel(new SavedStateHandle());
            ViewModelStore store = new ViewModelStore(); store.put("booking", vm);
            try {
                for (String method : new String[]{"cash", "vnpay", "pay_later"}) {
                    vm.confirmBooking("", method);
                    assertTrue(vm.getCreateBookingResult().getValue().isError());
                    assertNotNull(vm.getCreateBookingResult().getValue().message);
                }
            } finally { store.clear(); }
        });
    }
    @Test public void latestSlotsAndRestoredSelection() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SavedStateHandle state = new SavedStateHandle();
            state.set("date", "2099-01-01");
            state.set("slotId", "slot_1");
            BookingViewModel vm = new BookingViewModel(state);
            ViewModelStore store = new ViewModelStore(); store.put("booking", vm);
            try {
                TimeSlot slot = new TimeSlot("slot_1", "06:00", "07:30", false, 0);
                assertEquals("2099-01-01", vm.getSelectedDate());
                vm.getAvailableSlots().setValue(Resource.success(Collections.singletonList(slot)));
                assertSame(slot, vm.getSelectedSlot());
                vm.getAvailableSlots().setValue(Resource.loading(null));
                vm.onSlotSelected(slot);
                assertNull(vm.getSelectedSlot());
                vm.getAvailableSlots().setValue(Resource.success(Collections.singletonList(slot)));
                assertSame(slot, vm.getSelectedSlot());
                slot.setAvailable(false);
                vm.getAvailableSlots().setValue(Resource.success(Collections.singletonList(slot)));
                assertNull(vm.getSelectedSlot());
                assertNull(state.get("slotId"));
            } finally { store.clear(); }
        });
    }
    @Test public void serviceIdentityAndDateReset() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            SavedStateHandle state = new SavedStateHandle();
            BookingViewModel vm = new BookingViewModel(state);
            ViewModelStore store = new ViewModelStore(); store.put("booking", vm);
            try {
                Service a = new Service(); a.setServiceId("water"); a.setPrice(100);
                Service b = new Service(); b.setServiceId("water"); b.setPrice(200);
                vm.toggleService(a, true); vm.toggleService(b, true);
                assertEquals(200, vm.getServicePrice(), 0);
                vm.toggleService(a, false);
                assertEquals(0, vm.getServicePrice(), 0);
                vm.toggleService(a, true);
                vm.onDateSelected(System.currentTimeMillis() + 86400000L);
                assertFalse(vm.isServiceSelected(a));
                assertTrue(((java.util.List<?>) state.get("serviceIds")).isEmpty());
            } finally { store.clear(); }
        });
    }
}
