package com.example.hospitalappointment;

import android.content.Context;
import android.widget.EditText;
import android.view.ViewGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HospitalFlowTest {
    private Context testContext;
    private LocalStore store;
    private String tomorrow;

    @Before public void prepareIsolatedData() {
        // Test APK storage is separate from the application's patient records.
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        testContext.getSharedPreferences("hospital_local_v1", Context.MODE_PRIVATE).edit().clear().commit();
        store = new LocalStore(testContext);
        Calendar date = Calendar.getInstance(); date.add(Calendar.DAY_OF_YEAR,1);
        tomorrow = LocalStore.dateKey(date);
    }
    @Test public void accountRolesAndRegistrationPersist() {
        assertTrue(store.authenticate(LocalStore.PATIENT_EMAIL,"patient123",false));
        assertFalse(store.authenticate(LocalStore.PATIENT_EMAIL,"patient123",true));
        assertFalse(store.authenticate(LocalStore.ADMIN_EMAIL,"admin123",false));
        assertTrue(store.authenticate(LocalStore.ADMIN_EMAIL,"admin123",true));
        assertTrue(store.register("Jamie Lee","jamie@example.com","secret123"));
        assertFalse(store.register("Duplicate","jamie@example.com","different"));
        LocalStore restored = new LocalStore(testContext);
        assertTrue(restored.authenticate("jamie@example.com","secret123",false));
        assertFalse(restored.authenticate("jamie@example.com","wrong",false));
    }
    @Test public void bookingReservationStatusAndAvailabilityPersist() {
        LocalStore.Doctor first = store.doctors.get(0), second = store.doctors.get(1);
        LocalStore.Appointment visit = store.book(first,LocalStore.PATIENT_EMAIL,tomorrow,"09:00");
        assertNotNull(visit); assertEquals(LocalStore.PENDING,visit.status);
        assertNull(store.book(first,LocalStore.PATIENT_EMAIL,tomorrow,"09:00"));
        assertNull(store.book(second,LocalStore.PATIENT_EMAIL,tomorrow,"09:00"));
        assertFalse(store.setSlot(first.id,tomorrow,"09:00",false));
        visit.status = LocalStore.CONFIRMED; store.save();
        LocalStore adminView = new LocalStore(testContext);
        assertEquals(LocalStore.CONFIRMED,adminView.appointments.get(0).status);
        adminView.appointments.get(0).status = LocalStore.CANCELLED; adminView.save();
        LocalStore patientView = new LocalStore(testContext);
        assertFalse(patientView.reserved(first.id,tomorrow,"09:00"));
        assertTrue(patientView.setSlot(first.id,tomorrow,"09:00",false));
        assertNull(patientView.book(patientView.doctor(first.id),LocalStore.PATIENT_EMAIL,tomorrow,"09:00"));
        assertTrue(patientView.setSlot(first.id,tomorrow,"17:00",true));
        assertNotNull(patientView.book(patientView.doctor(first.id),LocalStore.PATIENT_EMAIL,tomorrow,"17:00"));
        patientView.removeDoctor(patientView.doctor(first.id));
        assertNull(patientView.doctor(first.id));
        assertEquals(LocalStore.CANCELLED,patientView.appointments.get(1).status);
        assertEquals(2,new LocalStore(testContext).appointments.size());
    }
    @Test public void patientAndAdminScreensInflate() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalStore appStore = new LocalStore(app); String original = appStore.session();
        try {
            appStore.setSession("");
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    assertNotNull(activity.findViewById(R.id.sign_in));
                    activity.findViewById(R.id.demo_login).performClick();
                    assertEquals("patient",((EditText)activity.findViewById(R.id.auth_email)).getText().toString());
                    activity.findViewById(R.id.sign_in).performClick();
                    assertNotNull(activity.findViewById(R.id.home_doctors));
                    BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                    nav.setSelectedItemId(R.id.nav_doctors);
                    assertTrue(((ViewGroup)activity.findViewById(R.id.doctor_list)).getChildCount() > 0);
                    ((ViewGroup)activity.findViewById(R.id.doctor_list)).getChildAt(0).performClick();
                    assertNotNull(activity.findViewById(R.id.detail_name));
                    activity.findViewById(R.id.book_doctor).performClick();
                    assertNotNull(activity.findViewById(R.id.slot_chips));
                    nav.setSelectedItemId(R.id.nav_appointments);
                    assertNotNull(activity.findViewById(R.id.appointment_list));
                    nav.setSelectedItemId(R.id.nav_profile);
                    assertNotNull(activity.findViewById(R.id.profile_email));
                });
            }
            appStore.setSession(LocalStore.ADMIN_EMAIL);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> {
                    assertNotNull(activity.findViewById(R.id.sign_in));
                    activity.findViewById(R.id.admin_demo_login).performClick();
                    activity.findViewById(R.id.sign_in).performClick();
                    assertEquals(4,((ViewGroup)activity.findViewById(R.id.stats_grid)).getChildCount());
                    BottomNavigationView nav = activity.findViewById(R.id.bottom_nav);
                    nav.setSelectedItemId(R.id.nav_appointments);
                    assertNotNull(activity.findViewById(R.id.status_chips));
                    nav.setSelectedItemId(R.id.nav_doctors);
                    assertNotNull(activity.findViewById(R.id.add_doctor));
                    nav.setSelectedItemId(R.id.nav_availability);
                    assertNotNull(activity.findViewById(R.id.availability_doctor));
                });
            }
        } finally { appStore.setSession(original); }
    }
    @Test public void mockLoginRoutesAndKeepsPatientIdentity() {
        assertEquals(LocalStore.ADMIN_EMAIL,store.mockSignIn("admin","admin123"));
        assertEquals("",store.mockSignIn("admin","wrong"));
        assertEquals("",store.mockSignIn("","password"));
        assertEquals("",store.mockSignIn("alice"," "));
        String alice = store.mockSignIn("Alice","anything");
        assertFalse(alice.isEmpty()); assertNotEquals(LocalStore.ADMIN_EMAIL,alice);
        assertEquals(alice,new LocalStore(testContext).mockSignIn("alice","a different password"));
        assertNotEquals(alice,store.mockSignIn("bob","anything"));
        assertNotEquals(LocalStore.ADMIN_EMAIL,store.mockSignIn(LocalStore.ADMIN_EMAIL,"anything"));
    }
    @Test public void patientAvailabilityMatchesAdminSchedule() {
        LocalStore.Doctor d = store.doctors.get(0), other = store.doctors.get(1);
        for (String time : store.slots(d.id,tomorrow)) assertTrue(store.setSlot(d.id,tomorrow,time,false));
        assertFalse(store.availableDates(d.id,LocalStore.PATIENT_EMAIL).contains(tomorrow));
        assertTrue(store.setSlot(d.id,tomorrow,"10:00",true));
        assertTrue(store.setSlot(d.id,tomorrow,"10:30",true));
        assertTrue(store.availableDates(d.id,LocalStore.PATIENT_EMAIL).contains(tomorrow));
        assertNotNull(store.book(other,LocalStore.PATIENT_EMAIL,tomorrow,"10:00"));
        assertFalse(store.availableSlots(d.id,tomorrow,LocalStore.PATIENT_EMAIL).contains("10:00"));
        assertTrue(store.availableSlots(d.id,tomorrow,LocalStore.PATIENT_EMAIL).contains("10:30"));
        assertNotNull(store.book(d,LocalStore.PATIENT_EMAIL,tomorrow,"10:30"));
        assertFalse(store.availableDates(d.id,LocalStore.PATIENT_EMAIL).contains(tomorrow));
        store.appointments.get(1).status = LocalStore.CANCELLED; store.save();
        LocalStore restored = new LocalStore(testContext);
        assertTrue(restored.availableSlots(d.id,tomorrow,LocalStore.PATIENT_EMAIL).contains("10:30"));
        Calendar farAway = Calendar.getInstance(); farAway.add(Calendar.DAY_OF_YEAR,31);
        assertNull(store.book(d,LocalStore.PATIENT_EMAIL,LocalStore.dateKey(farAway),"10:30"));
        assertNull(store.book(d,LocalStore.ADMIN_EMAIL,tomorrow,"10:30"));
    }
}
