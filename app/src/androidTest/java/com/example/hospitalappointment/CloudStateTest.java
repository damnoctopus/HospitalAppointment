package com.example.hospitalappointment;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Calendar;
import java.util.Map;
import static org.junit.Assert.*;

/** Tests cloud serialization and transaction working copies without a live project. */
@RunWith(AndroidJUnit4.class)
public class CloudStateTest {
    private LocalStore local;
    @Before public void setup() {
        Context context=InstrumentationRegistry.getInstrumentation().getContext();
        context.getSharedPreferences("hospital_local_v1",Context.MODE_PRIVATE).edit().clear().commit();
        local=new LocalStore(context);
    }
    @Test public void cloudPayloadExcludesCredentialsAndFailedWorkingCopyDoesNotPersist() {
        Map<String,Object> payload=local.cloudData();
        assertEquals(4,payload.size()); assertFalse(payload.containsKey("users"));
        assertFalse(payload.toString().contains("hash")); assertFalse(payload.toString().contains("salt"));
        LocalStore working=local.cloudCopy(payload);
        working.removeDoctor(working.doctors.get(0));
        assertEquals(4,local.doctors.size());
        assertEquals(4,new LocalStore(InstrumentationRegistry.getInstrumentation().getContext()).doctors.size());
        local.applyCloudData(working.cloudData()); assertEquals(3,local.doctors.size());
        assertTrue(local.authenticate(LocalStore.PATIENT_EMAIL,"patient123",false));
    }
    @Test public void retryAgainstLatestStateRejectsACompetingBooking() {
        Calendar day=Calendar.getInstance(); day.add(Calendar.DAY_OF_YEAR,1);
        String date=LocalStore.dateKey(day),id=local.doctors.get(0).id;
        LocalStore first=local.cloudCopy(local.cloudData());
        assertNotNull(first.book(first.doctor(id),LocalStore.PATIENT_EMAIL,date,"09:00"));
        LocalStore retry=local.cloudCopy(first.cloudData());
        assertNull(retry.book(retry.doctor(id),LocalStore.PATIENT_EMAIL,date,"09:00"));
        local.applyCloudData(first.cloudData());
        assertEquals(1,local.appointments.size());
        assertTrue(local.reserved(id,date,"09:00"));
    }
}
