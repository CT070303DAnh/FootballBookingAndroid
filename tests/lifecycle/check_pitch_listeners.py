"""Compile the real PitchRepository against deterministic Java test doubles.

No Firebase connection. Delivers callbacks after remove() to simulate callbacks
already queued on the Android main thread. Requires javac/java on PATH.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'app/src/main/java/com/example/footballbooking'
SOURCES = {
'androidx/lifecycle/MutableLiveData.java': '''package androidx.lifecycle;
public class MutableLiveData<T> { public T value; public int emissions;
public void setValue(T v) { value=v; emissions++; } }''',
'com/example/footballbooking/data/model/Pitch.java': '''package com.example.footballbooking.data.model;
public class Pitch { public String status="available"; public String getStatus(){return status;}
public float getRating(){return 0;} }''',
'com/example/footballbooking/utils/Constants.java': '''package com.example.footballbooking.utils;
public class Constants { public static final String COL_PITCHES="pitches", PITCH_MAINTENANCE="maintenance", PITCH_CLOSED="closed", PITCH_AVAILABLE="available"; }''',
'com/google/firebase/firestore/ListenerRegistration.java': '''package com.google.firebase.firestore;
public interface ListenerRegistration { void remove(); }''',
'com/google/firebase/firestore/DocumentSnapshot.java': '''package com.google.firebase.firestore;
public class DocumentSnapshot { public boolean exists(){return false;}
public <T> T toObject(Class<T> type){return null;} }''',
'com/google/firebase/firestore/QuerySnapshot.java': '''package com.google.firebase.firestore;
import java.util.*;
public class QuerySnapshot implements Iterable<DocumentSnapshot> {
public boolean isEmpty(){return true;}
public Iterator<DocumentSnapshot> iterator(){return Collections.emptyIterator();}
public <T> List<T> toObjects(Class<T> type){return new ArrayList<>();} }''',
'com/google/firebase/firestore/Task.java': '''package com.google.firebase.firestore;
import java.util.function.Consumer;
public class Task<T> { public Consumer<T> success; public Consumer<Exception> failure;
public Task<T> addOnSuccessListener(Consumer<T> c){success=c;return this;}
public Task<T> addOnFailureListener(Consumer<Exception> c){failure=c;return this;} }''',
'com/google/firebase/firestore/Query.java': '''package com.google.firebase.firestore;
import java.util.function.BiConsumer;
public class Query {
public static class Registration implements ListenerRegistration {
public boolean removed; public BiConsumer<QuerySnapshot,Exception> callback;
public void remove(){removed=true;}
public void emit(){callback.accept(new QuerySnapshot(),null);} }
public Query whereEqualTo(String k,String v){return this;}
public Query whereGreaterThanOrEqualTo(String k,String v){return this;}
public Query whereLessThanOrEqualTo(String k,String v){return this;}
public ListenerRegistration addSnapshotListener(BiConsumer<QuerySnapshot,Exception> c){
Registration r=new Registration();r.callback=c;FirebaseFirestore.listeners.add(r);return r;}
public Task<QuerySnapshot> get(){Task<QuerySnapshot> t=new Task<>();FirebaseFirestore.requests.add(t);return t;}
public DocumentReference document(String id){return new DocumentReference();} }''',
'com/google/firebase/firestore/DocumentReference.java': '''package com.google.firebase.firestore;
public class DocumentReference { public Task<DocumentSnapshot> get(){return new Task<>();} }''',
'com/google/firebase/firestore/FirebaseFirestore.java': '''package com.google.firebase.firestore;
import java.util.*;
public class FirebaseFirestore {
private static final FirebaseFirestore INSTANCE=new FirebaseFirestore();
public static final List<Query.Registration> listeners=new ArrayList<>();
public static final List<Task<QuerySnapshot>> requests=new ArrayList<>();
public static FirebaseFirestore getInstance(){return INSTANCE;}
public Query collection(String name){return new Query();} }''',
'ListenerRegression.java': '''import androidx.lifecycle.MutableLiveData;
import com.example.footballbooking.data.repository.PitchRepository;
import com.example.footballbooking.data.model.Pitch;
import com.example.footballbooking.utils.Resource;
import com.google.firebase.firestore.*;
import java.util.List;
public class ListenerRegression {
static int checks;
static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
public static void main(String[] args){
PitchRepository home=PitchRepository.createScoped(), ai=PitchRepository.createScoped();
MutableLiveData<Resource<List<Pitch>>> h=new MutableLiveData<>(), a=new MutableLiveData<>();
home.getAllPitches(h);Query.Registration homeListener=FirebaseFirestore.listeners.get(0);
ai.getAllPitches(a);Query.Registration aiListener=FirebaseFirestore.listeners.get(1);
check(!homeListener.removed,"AI must not unsubscribe Home");
ai.detachPitchListListener();check(aiListener.removed&&!homeListener.removed,"AI cleanup owns only AI listener");
homeListener.emit();check(h.value.isSuccess(),"Home still receives updates after AI cleanup");
int oldAi=a.emissions;aiListener.emit();check(a.emissions==oldAi,"Queued callback after cleanup is ignored");
home.searchPitchesByName("A",h);Task<QuerySnapshot> first=FirebaseFirestore.requests.get(0);
check(homeListener.removed,"Search removes previous realtime filter");
int before=h.emissions;homeListener.emit();check(h.emissions==before,"Queued old list cannot overwrite search");
home.searchPitchesByName("B",h);Task<QuerySnapshot> second=FirebaseFirestore.requests.get(1);
second.success.accept(new QuerySnapshot());before=h.emissions;
first.success.accept(new QuerySnapshot());check(h.emissions==before,"Older search success cannot overwrite newer search");
first.failure.accept(new Exception("old failure"));check(h.emissions==before,"Older search error cannot overwrite newer search");
home.detachPitchListListener();before=h.emissions;
second.success.accept(new QuerySnapshot());check(h.emissions==before,"Search callback after owner cleanup is ignored");
home.getPitchesByType("5",h);Query.Registration filter=FirebaseFirestore.listeners.get(2);
home.getAllPitches(h);check(filter.removed,"Reload replaces only previous owned filter");
before=h.emissions;filter.emit();check(h.emissions==before,"Old filter callback ignored");
FirebaseFirestore.listeners.get(3).emit();check(h.value.isSuccess(),"Replacement listener delivers data");
System.out.println(checks+" listener regression checks passed");
} }''',
}

with tempfile.TemporaryDirectory(prefix='pitch-listener-test-') as directory:
    temp = Path(directory)
    files = []
    for name, source in SOURCES.items():
        path = temp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
        files.append(str(path))
    files += [str(BASE / 'data/repository/PitchRepository.java'), str(BASE / 'utils/Resource.java')]
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(temp / 'classes'), *files], check=True)
    subprocess.run(['java', '-cp', str(temp / 'classes'), 'ListenerRegression'], check=True)
