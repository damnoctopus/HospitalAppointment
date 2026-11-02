package com.example.hospitalappointment;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.UUID;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.assertion.ViewAssertions.matches;

/** Exercises the real dialogs and shared records. Only its temporary doctor and visits are removed. */
@RunWith(AndroidJUnit4.class)
public class PatientBookingTest {
    @Test public void patientBooksAdminConfirmsAndPatientCancels() {
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LocalStore data = new LocalStore(app); String previousSession = data.session();
        // Remove only the legacy temporary fixtures created by earlier versions of this test.
        for (LocalStore.Doctor old : new java.util.ArrayList<>(data.doctors)) {
            if (old.name.equals("Dr. Booking Test") && old.qualification.equals("MBBS, MD") && old.experience == 8) {
                data.appointments.removeIf(a -> a.doctorId.equals(old.id)); data.removeDoctor(old);
            }
        }
        String id = UUID.randomUUID().toString();
        LocalStore.Doctor doctor = new LocalStore.Doctor(id,"Dr. Booking Test " + id.substring(0,8),"General Medicine","MBBS, MD",8,0);
        data.doctors.add(doctor); data.save();
        try {
            Calendar day = Calendar.getInstance(); day.add(Calendar.DAY_OF_YEAR,1);
            String date = LocalStore.dateKey(day);
            assertFalse(data.availableSlots(id,date,LocalStore.PATIENT_EMAIL).isEmpty());
            String time = data.availableSlots(id,date,LocalStore.PATIENT_EMAIL).get(0);
            for (String slot : data.slots(id,LocalStore.today())) data.setSlot(id,LocalStore.today(),slot,false);
            for (String slot : data.slots(id,date)) if (!slot.equals(time)) data.setSlot(id,date,slot,false);
            data.setSession(LocalStore.PATIENT_EMAIL);

            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> login(activity,false));
                onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));
                scenario.onActivity(activity -> {
                    ((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_doctors);
                    ((EditText)activity.findViewById(R.id.doctor_search)).setText(doctor.name);
                    assertEquals(1,((ViewGroup)activity.findViewById(R.id.doctor_list)).getChildCount());
                    ((ViewGroup)activity.findViewById(R.id.doctor_list)).getChildAt(0).performClick();
                    activity.findViewById(R.id.book_doctor).performClick();
                    assertEquals(LocalStore.displayDate(date),((TextView)activity.findViewById(R.id.select_date)).getText().toString());
                    assertFalse(activity.findViewById(R.id.confirm_booking).isEnabled());
                    ViewGroup slots = activity.findViewById(R.id.slot_chips); assertEquals(1,slots.getChildCount());
                    ((Chip)slots.getChildAt(0)).performClick();
                    assertTrue(activity.findViewById(R.id.confirm_booking).isEnabled());
                    activity.findViewById(R.id.confirm_booking).performClick();
                });
                onView(withText("Confirm request")).inRoot(isDialog()).perform(click());
                onView(withText("View my appointments")).inRoot(isDialog()).perform(click());
                scenario.onActivity(activity -> assertNotNull(activity.findViewById(R.id.appointment_list)));
                assertEquals(LocalStore.PENDING,findVisit(new LocalStore(app),id).status);
            }

            data.setSession(LocalStore.ADMIN_EMAIL);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> login(activity,true));
                onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));
                scenario.onActivity(activity -> {
                    ((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_appointments);
                    appointmentAction(activity,doctor.name).performClick();
                });
                onView(withText("Confirm")).inRoot(isDialog()).perform(click());
                assertEquals(LocalStore.CONFIRMED,findVisit(new LocalStore(app),id).status);
            }

            data.setSession(LocalStore.PATIENT_EMAIL);
            try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity -> login(activity,false));
                onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));
                scenario.onActivity(activity -> {
                    ((BottomNavigationView)activity.findViewById(R.id.bottom_nav)).setSelectedItemId(R.id.nav_appointments);
                    appointmentAction(activity,doctor.name).performClick();
                });
                onView(withText("Cancel visit")).inRoot(isDialog()).perform(click());
                LocalStore restored = new LocalStore(app);
                assertEquals(LocalStore.CANCELLED,findVisit(restored,id).status);
                assertTrue(restored.availableSlots(id,date,LocalStore.PATIENT_EMAIL).contains(time));
            }
        } finally {
            LocalStore cleanup = new LocalStore(app);
            cleanup.appointments.removeIf(a -> a.doctorId.equals(id));
            if (cleanup.doctor(id) != null) cleanup.removeDoctor(cleanup.doctor(id));
            cleanup.save(); cleanup.setSession(previousSession);
            // Flush asynchronous writes before instrumentation shuts down the process.
            app.getSharedPreferences("hospital_local_v1",Context.MODE_PRIVATE).edit().commit();
        }
    }
    private LocalStore.Appointment findVisit(LocalStore data,String doctorId) {
        for (LocalStore.Appointment visit : data.appointments) if (visit.doctorId.equals(doctorId)) return visit;
        throw new AssertionError("The patient's booking was not saved");
    }
    private void login(MainActivity activity,boolean admin) {
        activity.findViewById(admin ? R.id.admin_demo_login : R.id.demo_login).performClick();
        activity.findViewById(R.id.sign_in).performClick();
    }
    private View appointmentAction(MainActivity activity,String doctorName) {
        ViewGroup list = activity.findViewById(R.id.appointment_list);
        for (int i=0;i<list.getChildCount();i++) {
            View card = list.getChildAt(i); TextView name = card.findViewById(R.id.appointment_doctor);
            if (name != null && name.getText().toString().equals(doctorName))
                return ((ViewGroup)card.findViewById(R.id.appointment_actions)).getChildAt(0);
        }
        throw new AssertionError("Booking card was not shown");
    }
}
