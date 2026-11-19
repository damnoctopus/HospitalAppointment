package com.example.hospitalappointment;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** XML screens with direct handlers, backed by the device-only LocalStore. */
public class MainActivity extends AppCompatActivity {
    private LocalStore store;
    private CloudStore cloud;
    private LinearLayout content;
    private MaterialToolbar toolbar;
    private BottomNavigationView nav;
    private String email = "", screen = "home", selectedDoctor = "", selectedDate = LocalStore.today();
    private String selectedSlot = "", doctorQuery = "", specialty = "All", appointmentFilter = "Upcoming";
    private boolean admin;
    private int currentTab = R.id.nav_home;
    private final int[] portraits = {R.drawable.doctor_0, R.drawable.doctor_1, R.drawable.doctor_2, R.drawable.doctor_3};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new LocalStore(this);
        // A fresh launch always asks for mock credentials; rotation keeps the current screen.
        email = state == null ? "" : state.getString("signed_in_email", "");
        admin = email.equals(LocalStore.ADMIN_EMAIL);
        if (state != null) {
            screen = state.getString("screen", "home"); currentTab = state.getInt("tab", R.id.nav_home);
            selectedDoctor = state.getString("doctor", ""); selectedDate = state.getString("date", LocalStore.today());
            selectedSlot = state.getString("slot", ""); doctorQuery = state.getString("query", "");
            specialty = state.getString("specialty", "All"); appointmentFilter = state.getString("filter", "Upcoming");
        }
        EdgeToEdge.enable(this); setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        content = findViewById(R.id.content); toolbar = findViewById(R.id.toolbar); nav = findViewById(R.id.bottom_nav);
        findViewById(R.id.screen_host).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            int padding = Math.max(dp(20), (r-l-dp(680))/2);
            content.setPadding(padding, dp(20), padding, dp(24));
        });
        nav.setOnItemSelectedListener(item -> { switchTab(item.getItemId()); return true; });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (email.isEmpty()) finish();
                else if (screen.equals("booking")) showDetail(selectedDoctor);
                else if (screen.equals("detail")) navigate(currentTab);
                else if (currentTab != R.id.nav_home) navigate(R.id.nav_home);
                else finish();
            }
        });
        if (email.isEmpty()) showLogin();
        else {
            configureNav();
            if (screen.equals("booking") && store.doctor(selectedDoctor) != null) showBooking(selectedDoctor);
            else if (screen.equals("detail") && store.doctor(selectedDoctor) != null) showDetail(selectedDoctor);
            else navigate(currentTab);
        }
        cloud=new CloudStore(this,store,new CloudStore.Observer() {
            public void status(String value) { if (!isDestroyed()) text(findViewById(R.id.main),R.id.cloud_status,value); }
            public void changed() { if (!isDestroyed()) refreshScreen(); }
        });
        findViewById(R.id.cloud_status).setOnClickListener(view -> {
            if (cloud.configured()) cloud.start();
            else new MaterialAlertDialogBuilder(this).setTitle("Firebase setup")
                .setMessage(R.string.firebase_setup_help).setPositiveButton("OK",null).show();
        });
        cloud.start();
    }
    @Override protected void onDestroy() {
        if (cloud != null) cloud.close();
        super.onDestroy();
    }
    private void refreshScreen() {
        if (email.isEmpty() || !getWindow().getDecorView().hasWindowFocus()) return;
        if (screen.equals("detail")) showDetail(selectedDoctor);
        else if (screen.equals("booking")) showBooking(selectedDoctor);
        else if (!screen.equals("doctors")) switchTab(currentTab);
    }
    private void changeData(CloudStore.Mutation mutation,Runnable success) {
        String startingUser=email;
        cloud.change(mutation,error -> {
            if (isDestroyed()) return;
            if (error != null) { message(error.getMessage() == null ? "Could not save. Please retry." : error.getMessage()); refreshScreen(); }
            else if (email.equals(startingUser)) success.run();
        });
    }
    private void changeStatus(LocalStore.Appointment original,String status,Runnable refresh) {
        changeData(working -> {
            LocalStore.Appointment current=working.appointment(original.id);
            if (current == null || !current.status.equals(original.status))
                throw new IllegalStateException("This appointment changed. Please refresh and try again.");
            if (!current.active()) throw new IllegalStateException("This visit is already closed.");
            if (status.equals(LocalStore.CONFIRMED) && !LocalStore.future(current.date,current.time))
                throw new IllegalStateException("This appointment time has passed.");
            current.status=status;
        },refresh);
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putString("screen", screen); out.putInt("tab", currentTab); out.putString("doctor", selectedDoctor);
        out.putString("date", selectedDate); out.putString("slot", selectedSlot); out.putString("query", doctorQuery);
        out.putString("specialty", specialty); out.putString("filter", appointmentFilter);
        out.putString("signed_in_email", email);
        super.onSaveInstanceState(out);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private View inflate(int layout, ViewGroup parent) { return getLayoutInflater().inflate(layout, parent, false); }
    private View page(int layout, String title, boolean detail) {
        content.removeAllViews(); View v = inflate(layout, content); content.addView(v);
        toolbar.setTitle(title);
        if (detail) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
        } else {
            toolbar.setNavigationIcon((android.graphics.drawable.Drawable) null);
        }
        toolbar.setNavigationContentDescription("Back");
        toolbar.setNavigationOnClickListener(button -> getOnBackPressedDispatcher().onBackPressed());
        findViewById(R.id.scroll).scrollTo(0,0); nav.setVisibility(detail || email.isEmpty() ? View.GONE : View.VISIBLE);
        return v;
    }
    private void text(View root, int id, String value) { ((TextView)root.findViewById(id)).setText(value); }
    private String input(View root, int id) { return ((EditText)root.findViewById(id)).getText().toString().trim(); }
    private void click(View root, int id, Runnable action) { root.findViewById(id).setOnClickListener(v -> action.run()); }
    private void message(String value) { Snackbar.make(findViewById(R.id.main), value, Snackbar.LENGTH_LONG).show(); }
    private void confirm(String title, String description, String action, Runnable accepted) {
        new MaterialAlertDialogBuilder(this).setTitle(title).setMessage(description)
                .setNegativeButton("Keep", null).setPositiveButton(action, (d,w) -> accepted.run()).show();
    }
    private void showLogin() {
        screen = "login";
        View v = page(R.layout.screen_login, "HospitalAppointment", false);
        toolbar.setVisibility(View.GONE);
        click(v,R.id.demo_login,() -> {
            text(v,R.id.auth_email,"patient"); text(v,R.id.auth_password,"patient123");
        });
        click(v,R.id.admin_demo_login,() -> {
            text(v,R.id.auth_email,"admin"); text(v,R.id.auth_password,"admin123");
        });
        click(v,R.id.sign_in,() -> {
            String username = input(v,R.id.auth_email);
            String password = ((EditText)v.findViewById(R.id.auth_password)).getText().toString();
            if (username.isEmpty()) { ((EditText)v.findViewById(R.id.auth_email)).setError("Enter a username"); return; }
            if (password.trim().isEmpty()) { ((EditText)v.findViewById(R.id.auth_password)).setError("Enter a password"); return; }
            String identity = store.mockSignIn(username,password);
            if (identity.isEmpty()) { ((EditText)v.findViewById(R.id.auth_password)).setError("Admin password is admin123"); return; }
            email = identity; admin = email.equals(LocalStore.ADMIN_EMAIL);
            store.setSession(email); appointmentFilter = admin ? "All" : "Upcoming";
            toolbar.setVisibility(View.VISIBLE); configureNav(); navigate(R.id.nav_home);
            ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(v.getWindowToken(),0);
        });
    }
    private void configureNav() {
        toolbar.setVisibility(View.VISIBLE); nav.getMenu().clear(); nav.inflateMenu(admin ? R.menu.admin_nav : R.menu.patient_nav);
    }
    private void navigate(int id) { if (nav.getSelectedItemId() == id) switchTab(id); else nav.setSelectedItemId(id); }
    private void switchTab(int id) {
        currentTab = id;
        if (id == R.id.nav_home) { if (admin) showDashboard(); else showHome(); }
        else if (id == R.id.nav_doctors) showDoctors();
        else if (id == R.id.nav_appointments) showAppointments();
        else if (id == R.id.nav_availability && admin) showAvailability();
        else showProfile();
    }
    private interface Choice { void selected(String value); }
    private void choices(ChipGroup group, List<String> values, String selected, Choice listener) {
        group.removeAllViews();
        for (String value : values) {
            Chip chip = (Chip)inflate(R.layout.item_chip, group); chip.setId(View.generateViewId());
            chip.setText(value); group.addView(chip); chip.setChecked(value.equals(selected));
            chip.setOnCheckedChangeListener((b,checked) -> { if (checked) listener.selected(value); });
        }
    }
    private void empty(ViewGroup parent, String title, String body) {
        View v = inflate(R.layout.item_empty,parent); text(v,R.id.empty_title,title); text(v,R.id.empty_body,body); parent.addView(v);
    }
    private void doctorCard(ViewGroup list, LocalStore.Doctor d) {
        View v = inflate(R.layout.item_doctor,list); text(v,R.id.doctor_name,d.name);
        text(v,R.id.doctor_specialty,d.specialty); text(v,R.id.doctor_experience,d.experience + " years of experience");
        ((ImageView)v.findViewById(R.id.doctor_avatar)).setImageResource(portraits[d.portrait % portraits.length]);
        v.findViewById(R.id.doctor_availability).setVisibility(admin ? View.GONE : View.VISIBLE);
        if (!admin) {
            String date = store.nextAvailableDate(d.id,email);
            text(v,R.id.doctor_availability,date.isEmpty() ? getString(R.string.no_available_visits)
                    : getString(R.string.next_available,shortDate(date),
                    LocalStore.displayTime(store.availableSlots(d.id,date,email).get(0))));
        }
        v.setOnClickListener(button -> showDetail(d.id)); list.addView(v);
    }
    private void showHome() {
        screen = "home"; View v = page(R.layout.screen_home,"HospitalAppointment",false);
        text(v,R.id.greeting,"Hello, " + store.name(email).split(" ")[0]);
        click(v,R.id.home_search,() -> { doctorQuery = ""; specialty = "All"; navigate(R.id.nav_doctors); });
        click(v,R.id.view_all,() -> { doctorQuery = ""; specialty = "All"; navigate(R.id.nav_doctors); });
        List<String> specialties = store.specialties(); specialties.remove("All");
        choices(v.findViewById(R.id.specialty_chips),specialties,"",value -> { specialty = value; doctorQuery = ""; navigate(R.id.nav_doctors); });
        ViewGroup upcoming = v.findViewById(R.id.next_visit); boolean found = false;
        for (LocalStore.Appointment a : store.visits(email)) if (a.active() && LocalStore.future(a.date,a.time)) {
            appointmentCard(upcoming,a,this::showHome); found = true; break;
        }
        if (!found) empty(upcoming,"Your next step to better health","Book your first appointment with one of our doctors.");
        ViewGroup list = v.findViewById(R.id.home_doctors);
        for (int i = 0; i < Math.min(3,store.doctors.size()); i++) doctorCard(list,store.doctors.get(i));
        if (store.doctors.isEmpty()) empty(list,"Doctors will be here soon","The admin can add doctors from Manage Doctors.");
    }
    private void showDoctors() {
        screen = "doctors"; View v = page(R.layout.screen_doctors,admin ? "Manage doctors" : "Find doctors",false);
        if (admin) { text(v,R.id.list_title,"The people behind the care"); text(v,R.id.list_subtitle,"Add, update and manage your medical team."); }
        v.findViewById(R.id.add_doctor).setVisibility(admin ? View.VISIBLE : View.GONE); click(v,R.id.add_doctor,() -> doctorForm(null));
        if (!store.specialties().contains(specialty)) specialty = "All";
        choices(v.findViewById(R.id.filter_chips),store.specialties(),specialty,value -> { specialty = value; renderDoctors(v); });
        EditText search = v.findViewById(R.id.doctor_search); search.setText(doctorQuery);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) { doctorQuery = s.toString(); renderDoctors(v); }
            public void afterTextChanged(Editable s) { }
        }); renderDoctors(v);
    }
    private void renderDoctors(View root) {
        ViewGroup list = root.findViewById(R.id.doctor_list); list.removeAllViews(); String query = doctorQuery.trim().toLowerCase(Locale.ROOT);
        for (LocalStore.Doctor d : store.doctors)
            if ((specialty.equals("All") || d.specialty.equals(specialty)) && (d.name + " " + d.specialty).toLowerCase(Locale.ROOT).contains(query)) doctorCard(list,d);
        if (list.getChildCount() == 0) empty(list,"No doctors found","Try a different name or choose another specialty.");
    }
    private void showDetail(String id) {
        LocalStore.Doctor d = store.doctor(id); if (d == null) { navigate(R.id.nav_doctors); return; }
        selectedDoctor = id; screen = "detail"; View v = page(R.layout.screen_detail,"Doctor profile",true);
        ((ImageView)v.findViewById(R.id.detail_avatar)).setImageResource(portraits[d.portrait % portraits.length]);
        text(v,R.id.detail_name,d.name); text(v,R.id.detail_specialty,d.specialty);
        text(v,R.id.detail_about,d.qualification + "\n\n" + d.experience + " years of clinical experience in " + d.specialty.toLowerCase(Locale.ROOT) + ".");
        String nextDate = store.nextAvailableDate(d.id,admin ? null : email);
        text(v,R.id.detail_hours,nextDate.isEmpty() ? getString(R.string.no_bookable_dates)
                : LocalStore.displayDate(nextDate) + "\n" + joinTimes(store.availableSlots(d.id,nextDate,admin ? null : email)));
        v.findViewById(R.id.book_doctor).setVisibility(admin ? View.GONE : View.VISIBLE);
        v.findViewById(R.id.book_doctor).setEnabled(!nextDate.isEmpty());
        v.findViewById(R.id.edit_doctor).setVisibility(admin ? View.VISIBLE : View.GONE); v.findViewById(R.id.remove_doctor).setVisibility(admin ? View.VISIBLE : View.GONE);
        click(v,R.id.book_doctor,() -> { selectedDate = nextDate; selectedSlot = ""; showBooking(id); });
        click(v,R.id.edit_doctor,() -> doctorForm(d));
        click(v,R.id.remove_doctor,() -> confirm("Remove " + d.name + "?","Active appointments with this doctor will be cancelled. Previous visits will remain in appointment history.","Remove",() -> changeData(working -> { LocalStore.Doctor current=working.doctor(d.id); if (current != null) working.removeDoctor(current); },() -> navigate(R.id.nav_doctors))));
    }
    private String joinTimes(List<String> times) {
        StringBuilder result = new StringBuilder();
        for (String time : times) { if (result.length() > 0) result.append("  ·  "); result.append(LocalStore.displayTime(time)); } return result.toString();
    }
    private String shortDate(String date) {
        return new java.text.SimpleDateFormat("EEE, d MMM",Locale.getDefault())
                .format(LocalStore.calendar(date).getTime());
    }
    private void chooseDate(String current, Choice picked) {
        Calendar c = LocalStore.calendar(current);
        DatePickerDialog dialog = new DatePickerDialog(this,(view,y,m,d) -> {
            Calendar date = Calendar.getInstance(); date.set(y,m,d); picked.selected(LocalStore.dateKey(date));
        },c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH));
        Calendar start = Calendar.getInstance(); start.set(Calendar.HOUR_OF_DAY,0); start.set(Calendar.MINUTE,0); start.set(Calendar.SECOND,0); start.set(Calendar.MILLISECOND,0);
        dialog.getDatePicker().setMinDate(start.getTimeInMillis()); start.add(Calendar.DAY_OF_YEAR,30);
        dialog.getDatePicker().setMaxDate(start.getTimeInMillis()); dialog.show();
    }
    private void showBooking(String id) {
        if (admin) return;
        LocalStore.Doctor d = store.doctor(id); if (d == null) { navigate(R.id.nav_doctors); return; }
        screen = "booking"; selectedDoctor = id; View v = page(R.layout.screen_booking,"Book appointment",true);
        doctorCard(v.findViewById(R.id.booking_doctor),d); text(v,R.id.select_date,LocalStore.displayDate(selectedDate));
        click(v,R.id.select_date,() -> chooseDate(selectedDate,value -> { selectedDate = value; selectedSlot = ""; showBooking(id); }));
        List<String> dates = store.availableDates(id,email);
        text(v,R.id.available_dates_hint,getString(dates.isEmpty() ? R.string.no_bookable_dates : R.string.available_dates_hint));
        ChipGroup dateGroup = v.findViewById(R.id.date_chips);
        for (String date : dates) {
            Chip chip = (Chip)inflate(R.layout.item_chip,dateGroup); chip.setId(View.generateViewId());
            chip.setText(getString(R.string.date_slot_label,shortDate(date),store.availableSlots(id,date,email).size()));
            chip.setContentDescription(LocalStore.displayDate(date)); dateGroup.addView(chip);
            chip.setChecked(date.equals(selectedDate));
            if (chip.isChecked()) chip.post(() -> ((android.widget.HorizontalScrollView)dateGroup.getParent())
                    .scrollTo(Math.max(0,chip.getLeft()-dp(8)),0));
            chip.setOnCheckedChangeListener((button,checked) -> {
                if (checked) { selectedDate = date; selectedSlot = ""; showBooking(id); }
            });
        }
        ChipGroup group = v.findViewById(R.id.slot_chips);
        List<String> available = store.availableSlots(id,selectedDate,email);
        text(v,R.id.slot_count,getString(R.string.available_slot_count,available.size()));
        if (!available.contains(selectedSlot)) selectedSlot = "";
        for (String slot : available) {
            Chip chip = (Chip)inflate(R.layout.item_chip,group); chip.setId(View.generateViewId());
            chip.setText(LocalStore.displayTime(slot)); group.addView(chip); chip.setChecked(slot.equals(selectedSlot));
            chip.setOnCheckedChangeListener((b,checked) -> { if (checked) { selectedSlot = slot; updateBookingSummary(v,d); } });
        }
        v.findViewById(R.id.slot_empty).setVisibility(available.isEmpty() ? View.VISIBLE : View.GONE);
        v.findViewById(R.id.next_available_date).setVisibility(available.isEmpty() && !dates.isEmpty() ? View.VISIBLE : View.GONE);
        click(v,R.id.next_available_date,() -> { selectedDate = dates.get(0); selectedSlot = ""; showBooking(id); });
        updateBookingSummary(v,d);
        click(v,R.id.confirm_booking,() -> new MaterialAlertDialogBuilder(this).setTitle("Request this appointment?")
                .setMessage(d.name + "\n" + LocalStore.displayDate(selectedDate) + " at " + LocalStore.displayTime(selectedSlot))
                .setNegativeButton("Go back",null).setPositiveButton("Confirm request",(dialog,which) -> {
                    String date=selectedDate, time=selectedSlot, patient=email;
                    LocalStore.Appointment[] booked=new LocalStore.Appointment[1];
                    changeData(working -> {
                        booked[0]=working.book(working.doctor(d.id),patient,date,time);
                        if (booked[0] == null) throw new IllegalStateException("This slot is no longer available. Choose another time.");
                    },() -> {
                        LocalStore.Appointment a=booked[0];
                        new MaterialAlertDialogBuilder(this).setIcon(R.drawable.ic_check).setTitle("Appointment requested")
                            .setMessage("Your visit with " + a.doctorName + " is saved. The admin will confirm your booking.\n\n" + LocalStore.displayDate(a.date) + " · " + LocalStore.displayTime(a.time))
                            .setCancelable(false).setPositiveButton("View my appointments",(success,w) -> { appointmentFilter = "Upcoming"; navigate(R.id.nav_appointments); }).show();
                    });
                }).show());
    }
    private void updateBookingSummary(View v, LocalStore.Doctor doctor) {
        v.findViewById(R.id.confirm_booking).setEnabled(!selectedSlot.isEmpty());
        text(v,R.id.booking_summary,selectedSlot.isEmpty() ? getString(R.string.booking_summary_hint)
                : getString(R.string.booking_summary,doctor.name,LocalStore.displayDate(selectedDate),LocalStore.displayTime(selectedSlot)));
    }
    private void action(ViewGroup parent, String title, Runnable callback) {
        MaterialButton button = (MaterialButton)inflate(R.layout.item_action,parent); button.setText(title); button.setOnClickListener(v -> callback.run()); parent.addView(button);
    }
    private void appointmentCard(ViewGroup list, LocalStore.Appointment a, Runnable refresh) {
        View v = inflate(R.layout.item_appointment,list);
        text(v,R.id.appointment_status,a.status + (a.active() && !LocalStore.future(a.date,a.time) ? " · Past visit" : ""));
        if (a.status.equals(LocalStore.CANCELLED)) ((TextView)v.findViewById(R.id.appointment_status)).setTextColor(getColor(R.color.danger));
        text(v,R.id.appointment_doctor,a.doctorName); text(v,R.id.appointment_specialty,a.specialty);
        text(v,R.id.appointment_when,LocalStore.displayDate(a.date) + "\n" + LocalStore.displayTime(a.time) + " · 30 min consultation");
        text(v,R.id.appointment_patient,"Patient: " + a.patientName + "\n" + store.displayIdentity(a.patientEmail)); v.findViewById(R.id.appointment_patient).setVisibility(admin ? View.VISIBLE : View.GONE);
        ViewGroup actions = v.findViewById(R.id.appointment_actions);
        if (admin && a.status.equals(LocalStore.PENDING) && LocalStore.future(a.date,a.time))
            action(actions,"Confirm appointment",() -> confirm("Confirm appointment?",a.doctorName + " with " + a.patientName,"Confirm",() -> changeStatus(a,LocalStore.CONFIRMED,refresh)));
        if (admin && a.status.equals(LocalStore.CONFIRMED) && !LocalStore.future(a.date,a.time))
            action(actions,"Mark completed",() -> confirm("Mark visit completed?","This will move the visit into completed appointments.","Complete",() -> changeStatus(a,LocalStore.COMPLETED,refresh)));
        if (a.active()) action(actions,"Cancel appointment",() -> confirm("Cancel this appointment?","This will release the reserved slot for another patient.","Cancel visit",() -> changeStatus(a,LocalStore.CANCELLED,refresh)));
        list.addView(v);
    }
    private void showAppointments() {
        screen = "appointments"; View v = page(R.layout.screen_appointments,admin ? "Manage appointments" : "My appointments",false);
        if (admin) text(v,R.id.appointments_title,"Every visit matters.");
        List<String> filters = admin ? Arrays.asList("All","Pending","Confirmed","Completed","Cancelled") : Arrays.asList("Upcoming","Past","Completed","Cancelled");
        if (!filters.contains(appointmentFilter)) appointmentFilter = admin ? "All" : "Upcoming";
        choices(v.findViewById(R.id.status_chips),filters,appointmentFilter,value -> { appointmentFilter = value; renderAppointments(v); }); renderAppointments(v);
    }
    private void renderAppointments(View v) {
        ViewGroup list = v.findViewById(R.id.appointment_list); list.removeAllViews();
        for (LocalStore.Appointment a : store.visits(admin ? null : email)) {
            boolean include = appointmentFilter.equals("All") || a.status.equals(appointmentFilter);
            if (appointmentFilter.equals("Upcoming")) include = a.active() && LocalStore.future(a.date,a.time);
            if (appointmentFilter.equals("Past")) include = a.active() && !LocalStore.future(a.date,a.time);
            if (include) appointmentCard(list,a,() -> renderAppointments(v));
        }
        if (list.getChildCount() == 0) empty(list,"No " + appointmentFilter.toLowerCase(Locale.ROOT) + " appointments",admin ? "Patient bookings will appear here as they are requested." : "Your visits will appear here when you book an appointment.");
    }
    private void showProfile() {
        screen = "profile"; View v = page(R.layout.screen_profile,"Profile",false); text(v,R.id.profile_name,store.name(email)); text(v,R.id.profile_email,store.displayIdentity(email));
        click(v,R.id.profile_appointments,() -> navigate(R.id.nav_appointments)); click(v,R.id.logout,this::logout);
    }
    private void logout() {
        confirm("Log out?","Your local appointments will be saved on this device.","Log out",this::endSession);
    }
    private void endSession() {
        store.setSession(""); email = ""; admin = false;
        specialty = "All"; doctorQuery = ""; appointmentFilter = "Upcoming"; currentTab = R.id.nav_home;
        showLogin();
    }
    private void showDashboard() {
        screen = "dashboard"; View v = page(R.layout.screen_dashboard,"Admin dashboard",false); int today = 0, pending = 0;
        for (LocalStore.Appointment a : store.appointments) { if (a.date.equals(LocalStore.today())) today++; if (a.status.equals(LocalStore.PENDING)) pending++; }
        GridLayout grid = v.findViewById(R.id.stats_grid); String[] labels = {"Total doctors","Total appointments","Today's appointments","Pending requests"};
        int[] values = {store.doctors.size(),store.appointments.size(),today,pending};
        for (int i=0;i<labels.length;i++) { View stat = inflate(R.layout.item_stat,grid); text(stat,R.id.stat_value,String.valueOf(values[i])); text(stat,R.id.stat_label,labels[i]); grid.addView(stat); }
        click(v,R.id.dashboard_doctors,() -> navigate(R.id.nav_doctors)); click(v,R.id.dashboard_availability,() -> navigate(R.id.nav_availability));
        click(v,R.id.dashboard_appointments,() -> { appointmentFilter = "All"; navigate(R.id.nav_appointments); }); click(v,R.id.admin_logout,this::logout);
        click(v,R.id.dashboard_patient_login,() -> confirm("Sign in as a patient?",
                "This signs out of admin. Enter any patient username and a non-empty password to book a visit. All doctor schedules and appointments stay saved.",
                "Patient sign in",this::endSession));
        ViewGroup list = v.findViewById(R.id.today_list);
        for (LocalStore.Appointment a : store.visits(null)) if (a.date.equals(LocalStore.today())) appointmentCard(list,a,this::showDashboard);
        if (list.getChildCount() == 0) empty(list,"A clear schedule today","Appointments booked for today will appear here.");
    }
    private void doctorForm(LocalStore.Doctor doctor) {
        if (!admin) return; View form = inflate(R.layout.form_doctor,content);
        if (doctor != null) { text(form,R.id.form_name,doctor.name); text(form,R.id.form_specialty,doctor.specialty); text(form,R.id.form_qualification,doctor.qualification); text(form,R.id.form_experience,String.valueOf(doctor.experience)); }
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle(doctor == null ? "Add doctor" : "Edit doctor").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(shown -> dialog.getButton(-1).setOnClickListener(button -> {
            String name = input(form,R.id.form_name), special = input(form,R.id.form_specialty), qualification = input(form,R.id.form_qualification); boolean valid = true;
            for (int field : new int[]{R.id.form_name,R.id.form_specialty,R.id.form_qualification}) if (input(form,field).length() < 2) { ((EditText)form.findViewById(field)).setError("Please enter this detail"); valid = false; }
            int experience;
            try { experience = Integer.parseInt(input(form,R.id.form_experience)); if (experience < 0 || experience > 60) throw new NumberFormatException(); }
            catch (NumberFormatException e) { ((EditText)form.findViewById(R.id.form_experience)).setError("Enter 0–60 years"); return; }
            if (!valid) return;
            String doctorId=doctor == null ? UUID.randomUUID().toString() : doctor.id;
            int years=experience;
            dialog.getButton(-1).setEnabled(false);
            cloud.change(working -> {
                LocalStore.Doctor d=working.doctor(doctorId);
                if (doctor != null && d == null) throw new IllegalStateException("This doctor was removed on another device.");
                if (d == null) { d=new LocalStore.Doctor(doctorId,name,special,qualification,years,working.doctors.size()%4); working.doctors.add(d); }
                d.name=name; d.specialty=special; d.qualification=qualification; d.experience=years;
            },error -> {
                if (isDestroyed()) return;
                dialog.getButton(-1).setEnabled(true);
                if (error != null) { message(error.getMessage()); return; }
                dialog.dismiss();
                if (screen.equals("detail")) showDetail(doctorId); else showDoctors();
                message("Doctor saved");
            });
        })); dialog.show();
    }
    private void showAvailability() {
        screen = "availability"; View v = page(R.layout.screen_availability,"Manage availability",false);
        if (store.doctors.isEmpty()) { v.findViewById(R.id.add_slot).setEnabled(false); text(v,R.id.availability_empty,"Add a doctor before managing availability."); return; }
        if (store.doctor(selectedDoctor) == null) selectedDoctor = store.doctors.get(0).id;
        Spinner spinner = v.findViewById(R.id.availability_doctor);
        ArrayAdapter<LocalStore.Doctor> adapter = new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,store.doctors);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); spinner.setAdapter(adapter);
        for (int i=0;i<store.doctors.size();i++) if (store.doctors.get(i).id.equals(selectedDoctor)) spinner.setSelection(i);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent,View item,int position,long id) { selectedDoctor = store.doctors.get(position).id; renderAvailability(v); }
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        click(v,R.id.availability_date,() -> chooseDate(selectedDate,date -> { selectedDate = date; renderAvailability(v); }));
        click(v,R.id.add_slot,() -> new TimePickerDialog(this,(picker,hour,minute) -> {
            if (minute != 0 && minute != 30) { message("Choose a 30-minute slot, such as 10:00 or 10:30."); return; }
            String time = String.format(Locale.US,"%02d:%02d",hour,minute);
            if (!LocalStore.future(selectedDate,time)) { message("Choose a time in the future."); return; }
            String doctorId=selectedDoctor,date=selectedDate;
            changeData(working -> { if (!working.setSlot(doctorId,date,time,true)) throw new IllegalStateException("This slot already exists or the doctor was removed."); },this::showAvailability);
        },9,0,false).show()); renderAvailability(v);
    }
    private void renderAvailability(View v) {
        text(v,R.id.availability_date,LocalStore.displayDate(selectedDate)); ChipGroup group = v.findViewById(R.id.availability_slots); group.removeAllViews();
        List<String> slots = store.slots(selectedDoctor,selectedDate);
        for (String time : slots) {
            boolean reserved = store.reserved(selectedDoctor,selectedDate,time); Chip chip = (Chip)inflate(R.layout.item_chip,group); chip.setCheckable(false);
            chip.setText(LocalStore.displayTime(time) + (reserved ? " · Reserved" : "  ×")); chip.setContentDescription(LocalStore.displayTime(time) + (reserved ? ", reserved" : ", tap to remove"));
            chip.setOnClickListener(button -> {
                if (reserved) { message("Cancel the appointment before removing its time slot."); return; }
                confirm("Remove time slot?",LocalStore.displayDate(selectedDate) + " at " + LocalStore.displayTime(time),"Remove",() -> {
                    String doctorId=selectedDoctor,date=selectedDate;
                    changeData(working -> { if (!working.setSlot(doctorId,date,time,false)) throw new IllegalStateException("This slot was just reserved and cannot be removed."); },this::showAvailability);
                });
            }); group.addView(chip);
        }
        v.findViewById(R.id.availability_empty).setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
