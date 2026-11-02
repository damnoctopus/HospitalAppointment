package com.example.hospitalappointment;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Small device-only JSON store shared by the patient and admin screens. */
public class LocalStore {
    public static final String ADMIN_EMAIL = "admin@hospital.demo";
    public static final String PATIENT_EMAIL = "patient@demo.com";
    public static final String PENDING = "Pending", CONFIRMED = "Confirmed",
            COMPLETED = "Completed", CANCELLED = "Cancelled";
    public final List<Doctor> doctors = new ArrayList<>();
    public final List<Appointment> appointments = new ArrayList<>();
    private final SharedPreferences prefs;
    private JSONArray users;
    private JSONObject availability;

    public static class Doctor {
        public String id, name, specialty, qualification;
        public int experience, portrait;
        Doctor(String id, String name, String specialty, String qualification, int experience, int portrait) {
            this.id = id; this.name = name; this.specialty = specialty;
            this.qualification = qualification; this.experience = experience; this.portrait = portrait;
        }
        @Override public String toString() { return name + " · " + specialty; }
    }
    public static class Appointment {
        public String id, doctorId, doctorName, specialty, patientName, patientEmail, date, time, status;
        public boolean active() { return status.equals(PENDING) || status.equals(CONFIRMED); }
    }
    public LocalStore(Context context) {
        prefs = context.getSharedPreferences("hospital_local_v1", Context.MODE_PRIVATE);
        try {
            users = new JSONArray(prefs.getString("users", "[]"));
            availability = new JSONObject(prefs.getString("availability", "{}"));
            if (!prefs.contains("doctors")) {
                doctors.add(new Doctor("d1", "Dr. Aarav Mehta", "Cardiology", "MBBS, MD, DM (Cardiology)", 12, 0));
                doctors.add(new Doctor("d2", "Dr. Ananya Rao", "Dermatology", "MBBS, MD (Dermatology)", 8, 1));
                doctors.add(new Doctor("d3", "Dr. Rohan Shah", "General Medicine", "MBBS, MD (General Medicine)", 10, 2));
                doctors.add(new Doctor("d4", "Dr. Priya Nair", "Pediatrics", "MBBS, MD (Pediatrics)", 9, 3));
                addUser("Alex Morgan", PATIENT_EMAIL, "patient123");
                addUser("Hospital Admin", ADMIN_EMAIL, "admin123");
                save();
            } else {
                JSONArray ds = new JSONArray(prefs.getString("doctors", "[]"));
                for (int i = 0; i < ds.length(); i++) {
                    JSONObject d = ds.getJSONObject(i);
                    doctors.add(new Doctor(d.getString("id"), d.getString("name"), d.getString("specialty"),
                            d.getString("qualification"), d.getInt("experience"), d.getInt("portrait")));
                }
            }
            JSONArray as = new JSONArray(prefs.getString("appointments", "[]"));
            for (int i = 0; i < as.length(); i++) {
                JSONObject j = as.getJSONObject(i); Appointment a = new Appointment();
                a.id = j.getString("id"); a.doctorId = j.getString("doctorId");
                a.doctorName = j.getString("doctorName"); a.specialty = j.getString("specialty");
                a.patientName = j.getString("patientName"); a.patientEmail = j.getString("patientEmail");
                a.date = j.getString("date"); a.time = j.getString("time"); a.status = j.getString("status");
                appointments.add(a);
            }
        } catch (JSONException e) { throw new IllegalStateException("Cannot read local demo data", e); }
    }
    public String session() { return prefs.getString("session", ""); }
    public void setSession(String email) { prefs.edit().putString("session", email).apply(); }
    /** Mock routing only: patient passwords are not checked or saved. */
    public String mockSignIn(String username, String password) {
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || password.trim().isEmpty()) return "";
        if (normalized.equals("admin")) return password.equals("admin123") ? ADMIN_EMAIL : "";
        String identity = normalized.equals("patient") ? PATIENT_EMAIL : "mock:" + normalized;
        if (user(identity) == null) { addUser(username.trim(), identity, UUID.randomUUID().toString()); save(); }
        return identity;
    }
    public String displayIdentity(String identity) {
        if (identity.equals(PATIENT_EMAIL)) return "patient";
        return identity.startsWith("mock:") ? identity.substring(5) : identity;
    }
    public String name(String email) {
        JSONObject user = user(email);
        return user == null ? "Patient" : user.optString("name", "Patient");
    }
    private JSONObject user(String email) {
        for (int i = 0; i < users.length(); i++) {
            JSONObject u = users.optJSONObject(i);
            if (u != null && u.optString("email").equalsIgnoreCase(email)) return u;
        }
        return null;
    }
    public boolean authenticate(String email, String password, boolean admin) {
        if (admin != email.equalsIgnoreCase(ADMIN_EMAIL)) return false;
        JSONObject u = user(email);
        return u != null && u.optString("hash").equals(hash(u.optString("salt"), password));
    }
    public boolean register(String name, String email, String password) {
        if (user(email) != null) return false;
        addUser(name, email, password); save(); return true;
    }
    private void addUser(String name, String email, String password) {
        try {
            String salt = UUID.randomUUID().toString();
            users.put(new JSONObject().put("name", name).put("email", email.toLowerCase(Locale.ROOT))
                    .put("salt", salt).put("hash", hash(salt, password)));
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    private static String hash(String salt, String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((salt + password).getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (byte b : digest) s.append(String.format(Locale.ROOT, "%02x", b & 255));
            return s.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public Doctor doctor(String id) {
        for (Doctor d : doctors) if (d.id.equals(id)) return d;
        return null;
    }
    public List<String> specialties() {
        List<String> result = new ArrayList<>(); result.add("All");
        for (Doctor d : doctors) if (!result.contains(d.specialty)) result.add(d.specialty);
        return result;
    }
    public List<String> slots(String doctorId, String date) {
        JSONArray saved = availability.optJSONArray(doctorId + ":" + date);
        List<String> slots = new ArrayList<>();
        if (saved == null) slots.addAll(Arrays.asList("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "14:00", "14:30", "15:00", "15:30", "16:00"));
        else for (int i = 0; i < saved.length(); i++) slots.add(saved.optString(i));
        Collections.sort(slots); return slots;
    }
    public boolean reserved(String doctorId, String date, String time) {
        for (Appointment a : appointments)
            if (a.active() && a.doctorId.equals(doctorId) && a.date.equals(date) && a.time.equals(time)) return true;
        return false;
    }
    /** Only future, unreserved slots within the patient booking window are selectable. */
    public List<String> availableSlots(String doctorId, String date, String patientEmail) {
        List<String> available = new ArrayList<>();
        Calendar lastDay = Calendar.getInstance(); lastDay.add(Calendar.DAY_OF_YEAR, 30);
        if (doctor(doctorId) == null || date.compareTo(today()) < 0
                || date.compareTo(dateKey(lastDay)) > 0) return available;
        for (String time : slots(doctorId, date)) {
            if (!future(date, time) || reserved(doctorId, date, time)) continue;
            boolean patientBusy = false;
            for (Appointment a : appointments) {
                if (a.active() && a.patientEmail.equals(patientEmail)
                        && a.date.equals(date) && a.time.equals(time)) {
                    patientBusy = true; break;
                }
            }
            if (!patientBusy) available.add(time);
        }
        return available;
    }
    public List<String> availableDates(String doctorId, String patientEmail) {
        List<String> dates = new ArrayList<>();
        Calendar day = Calendar.getInstance();
        for (int i = 0; i <= 30; i++) {
            String date = dateKey(day);
            if (!availableSlots(doctorId, date, patientEmail).isEmpty()) dates.add(date);
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return dates;
    }
    public String nextAvailableDate(String doctorId, String patientEmail) {
        Calendar day = Calendar.getInstance();
        for (int i = 0; i <= 30; i++) {
            String date = dateKey(day);
            if (!availableSlots(doctorId, date, patientEmail).isEmpty()) return date;
            day.add(Calendar.DAY_OF_YEAR, 1);
        }
        return "";
    }
    public boolean setSlot(String doctorId, String date, String time, boolean add) {
        if (doctor(doctorId) == null || (!add && reserved(doctorId, date, time))) return false;
        List<String> slots = slots(doctorId, date);
        if (add) { if (slots.contains(time)) return false; slots.add(time); }
        else slots.remove(time);
        try { availability.put(doctorId + ":" + date, new JSONArray(slots)); }
        catch (JSONException e) { throw new IllegalStateException(e); }
        save(); return true;
    }
    public Appointment book(Doctor doctor, String email, String date, String time) {
        if (doctor == null || user(email) == null || email.equalsIgnoreCase(ADMIN_EMAIL)
                || !availableSlots(doctor.id, date, email).contains(time)) return null;
        Appointment a = new Appointment();
        a.id = UUID.randomUUID().toString(); a.doctorId = doctor.id; a.doctorName = doctor.name;
        a.specialty = doctor.specialty; a.patientEmail = email; a.patientName = name(email);
        a.date = date; a.time = time; a.status = PENDING;
        appointments.add(a); save(); return a;
    }
    public List<Appointment> visits(String email) {
        List<Appointment> result = new ArrayList<>();
        for (Appointment a : appointments) if (email == null || a.patientEmail.equals(email)) result.add(a);
        result.sort((a,b) -> (a.date + a.time).compareTo(b.date + b.time)); return result;
    }
    public void removeDoctor(Doctor doctor) {
        doctors.remove(doctor);
        for (Appointment a : appointments) if (a.doctorId.equals(doctor.id) && a.active()) a.status = CANCELLED;
        List<String> schedules = new ArrayList<>();
        java.util.Iterator<String> keys = availability.keys();
        while (keys.hasNext()) { String key = keys.next(); if (key.startsWith(doctor.id + ":")) schedules.add(key); }
        for (String key : schedules) availability.remove(key);
        save();
    }
    public void save() {
        try {
            JSONArray ds = new JSONArray(), as = new JSONArray();
            for (Doctor d : doctors) ds.put(new JSONObject().put("id", d.id).put("name", d.name)
                    .put("specialty", d.specialty).put("qualification", d.qualification)
                    .put("experience", d.experience).put("portrait", d.portrait));
            for (Appointment a : appointments) as.put(new JSONObject().put("id", a.id).put("doctorId", a.doctorId)
                    .put("doctorName", a.doctorName).put("specialty", a.specialty).put("patientName", a.patientName)
                    .put("patientEmail", a.patientEmail).put("date", a.date).put("time", a.time).put("status", a.status));
            prefs.edit().putString("doctors", ds.toString()).putString("appointments", as.toString())
                    .putString("users", users.toString()).putString("availability", availability.toString()).apply();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    public static String today() { return dateKey(Calendar.getInstance()); }
    public static String dateKey(Calendar date) { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date.getTime()); }
    public static Calendar calendar(String date) {
        Calendar c = Calendar.getInstance();
        try { c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid date", e); }
        return c;
    }
    public static String displayDate(String date) {
        return new SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(calendar(date).getTime());
    }
    public static String displayTime(String time) {
        try { Date t = new SimpleDateFormat("HH:mm", Locale.US).parse(time);
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(t);
        } catch (Exception e) { return time; }
    }
    public static boolean future(String date, String time) {
        try { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse(date + " " + time).after(new Date()); }
        catch (Exception e) { return false; }
    }
}
