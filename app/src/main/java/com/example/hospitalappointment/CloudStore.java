package com.example.hospitalappointment;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import java.util.Map;

/** One small shared demo document. All writes read current server data in a transaction. */
public final class CloudStore {
    public interface Mutation { void apply(LocalStore working); }
    public interface Observer {
        void status(String text);
        void changed();
    }
    public interface Completion { void done(Exception error); }
    private final LocalStore local;
    private final Observer observer;
    private final FirebaseApp app;
    private DocumentReference document;
    private ListenerRegistration listener;
    private boolean ready, busy, closed;

    public CloudStore(Context context,LocalStore local,Observer observer) {
        this.local=local; this.observer=observer;
        app=FirebaseApp.initializeApp(context);
    }
    public boolean configured() { return app != null; }
    public void start() {
        if (closed || busy) return;
        if (!configured()) { observer.status("Local demo · Firebase setup required"); return; }
        if (listener != null) { listener.remove(); listener=null; }
        ready=false; busy=true; observer.status("Connecting to cloud…");
        FirebaseAuth auth=FirebaseAuth.getInstance(app);
        if (auth.getCurrentUser() != null) connect();
        else auth.signInAnonymously().addOnSuccessListener(result -> connect())
                .addOnFailureListener(this::connectionFailed);
    }
    private void connect() {
        if (closed) return;
        FirebaseFirestore db=FirebaseFirestore.getInstance(app);
        document=db.document("hospitalDemos/collegeDemo");
        Map<String,Object> seed=local.cloudData();
        db.runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot=transaction.get(document);
            if (!snapshot.exists()) { transaction.set(document,seed); return seed; }
            return snapshot.getData();
        }).addOnSuccessListener(data -> {
            if (closed) return;
            try { local.applyCloudData(data); }
            catch (RuntimeException e) { connectionFailed(e); return; }
            busy=false; ready=true; observer.changed();
            listener=document.addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error) -> {
                if (closed) return;
                if (error != null) { connectionFailed(error); return; }
                if (snapshot == null || !snapshot.exists()) return;
                ready=!snapshot.getMetadata().isFromCache();
                if (!busy && ready) {
                    try { local.applyCloudData(snapshot.getData()); observer.changed(); }
                    catch (RuntimeException e) { connectionFailed(e); return; }
                }
                observer.status(busy ? "Saving to cloud…" : ready ? "Cloud connected · Shared demo" : "Offline · Saved data · Tap to retry");
            });
        }).addOnFailureListener(this::connectionFailed);
    }
    private void connectionFailed(Exception error) {
        if (closed) return;
        busy=false; ready=false;
        observer.status("Cloud unavailable · Check Firebase setup or connection · Tap to retry");
    }
    public void change(Mutation mutation,Completion completion) {
        if (busy) { completion.done(new IllegalStateException("Please wait for the current cloud request.")); return; }
        if (!configured()) {
            try {
                LocalStore working=local.cloudCopy(local.cloudData()); mutation.apply(working);
                local.applyCloudData(working.cloudData()); completion.done(null);
            } catch (Exception e) { completion.done(e); }
            return;
        }
        if (!ready) { completion.done(new IllegalStateException("Connect to cloud before saving. Tap the connection banner to retry.")); return; }
        busy=true; observer.status("Saving to cloud…");
        // Capture local mock profiles on the UI thread. Credentials are never uploaded.
        LocalStore template=local.cloudCopy(local.cloudData());
        FirebaseFirestore.getInstance(app).runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snapshot=transaction.get(document);
            if (!snapshot.exists()) throw new IllegalStateException("Cloud demo data is missing. Reconnect first.");
            LocalStore working=template.cloudCopy(snapshot.getData());
            mutation.apply(working); Map<String,Object> result=working.cloudData();
            transaction.set(document,result); return result;
        }).addOnSuccessListener(data -> {
            if (closed) return;
            busy=false; local.applyCloudData(data); observer.status("Cloud connected · Shared demo"); completion.done(null);
        }).addOnFailureListener(error -> {
            if (closed) return;
            busy=false; observer.status("Save failed · Tap to reconnect"); completion.done(error);
            start();
        });
    }
    public void close() { closed=true; if (listener != null) listener.remove(); }
}
