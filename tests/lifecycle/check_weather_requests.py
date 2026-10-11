"""Compile production WeatherRepository with deterministic HTTP/main-loop doubles."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "app/src/main/java/com/example/footballbooking"
SOURCES = {
"android/os/Looper.java": "package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }",
"android/os/SystemClock.java": "package android.os; public class SystemClock { public static long now; public static long elapsedRealtime(){return now;} }",
"android/os/Handler.java": """package android.os; import java.util.*; public class Handler {
static List<Runnable> queue=new ArrayList<>(); List<Runnable> owned=new ArrayList<>();
public Handler(Looper l){} public void post(Runnable r){queue.add(r);owned.add(r);}
public void removeCallbacksAndMessages(Object o){queue.removeAll(owned);owned.clear();}
public static void drain(){while(!queue.isEmpty())queue.remove(0).run();} }""",
"androidx/lifecycle/MutableLiveData.java": "package androidx.lifecycle; public class MutableLiveData<T>{public T value; public int count; public void setValue(T v){value=v;count++;}}",
"com/example/footballbooking/BuildConfig.java": 'package com.example.footballbooking; public class BuildConfig {public static final String OPENWEATHER_API_KEY="test";}',
"com/example/footballbooking/data/model/WeatherData.java": "package com.example.footballbooking.data.model; public class WeatherData {}",
"retrofit2/Callback.java": "package retrofit2; public interface Callback<T>{void onResponse(Call<T> c,Response<T> r);void onFailure(Call<T> c,Throwable t);}",
"retrofit2/Call.java": """package retrofit2; public class Call<T>{public Callback<T> callback;public boolean cancelled;
public void enqueue(Callback<T> c){callback=c;}public void cancel(){cancelled=true;}
public void success(T v){callback.onResponse(this,new Response<T>(v));}
public void fail(){callback.onFailure(this,new Exception());}}""",
"retrofit2/Response.java": "package retrofit2; public class Response<T>{T v; public Response(T x){v=x;}public boolean isSuccessful(){return v!=null;}public T body(){return v;}public int code(){return 500;}}",
"com/example/footballbooking/data/remote/api/WeatherApiService.java": """package com.example.footballbooking.data.remote.api;
import java.util.*;import retrofit2.*;import com.example.footballbooking.data.model.WeatherData;
public class WeatherApiService{ public List<Call<WeatherData>> calls=new ArrayList<>();
Call<WeatherData> next(){Call<WeatherData> c=new Call<>();calls.add(c);return c;}
public Call<WeatherData> getCurrentWeatherByCoords(double a,double b,String k,String u,String l){return next();}
public Call<WeatherData> getCurrentWeatherByCity(String c,String k,String u,String l){return next();}}""",
"com/example/footballbooking/data/remote/RetrofitClient.java": """package com.example.footballbooking.data.remote;
import com.example.footballbooking.data.remote.api.WeatherApiService;
public class RetrofitClient {static RetrofitClient instance=new RetrofitClient(); public WeatherApiService api=new WeatherApiService();
public static RetrofitClient getInstance(){return instance;}public WeatherApiService getWeatherApiService(){return api;}}""",
"WeatherRegression.java": """import android.os.*;import androidx.lifecycle.*;import retrofit2.*;
import com.example.footballbooking.data.repository.*;import com.example.footballbooking.data.remote.*;
import com.example.footballbooking.data.model.*;import com.example.footballbooking.utils.*;
public class WeatherRegression{static int checks;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);checks++;}
public static void main(String[] args){
WeatherRepository r=WeatherRepository.createScoped();MutableLiveData<Resource<WeatherData>> out=new MutableLiveData<>();
var calls=RetrofitClient.getInstance().api.calls;
r.getWeatherByCoords(1,2,out);var a=calls.get(0);WeatherData wa=new WeatherData();a.success(wa);Handler.drain();
r.getWeatherByCoords(1,2,out);check(calls.size()==1 && out.value.data==wa,"same location cache");
r.getWeatherByCoords(3,4,out);check(calls.size()==2 && out.value.isLoading(),"different location fetch");var b=calls.get(1);
r.getWeatherHanoi(out);var city=calls.get(2);check(b.cancelled,"cancel superseded request");
WeatherData wc=new WeatherData();city.success(wc);Handler.drain();int n=out.count;
b.success(new WeatherData());b.fail();Handler.drain();check(out.count==n && out.value.data==wc,"stale success and failure ignored");
r.getWeatherHanoi(out);check(calls.size()==3,"city cache");
r.getWeatherByCoords(3,4,out);check(calls.size()==4,"city cache not used for GPS");
var pending=calls.get(3);pending.success(new WeatherData());r.cancelPending();Handler.drain();check(out.value.isLoading(),"queued delivery removed on cleanup");
n=out.count;pending.fail();Handler.drain();check(n==out.count,"late callback after cleanup");
r.clearCache();r.getWeatherHanoi(out);check(calls.size()==5,"refresh bypasses cache");calls.get(4).success(wc);Handler.drain();
SystemClock.now=600001;r.getWeatherHanoi(out);check(calls.size()==6,"cache expires");
var other=WeatherRepository.createScoped();var otherOut=new MutableLiveData<Resource<WeatherData>>();other.getWeatherHanoi(otherOut);var otherCall=calls.get(6);
r.cancelPending();check(!otherCall.cancelled,"owners isolated");otherCall.fail();Handler.drain();check(otherOut.value.isError(),"current failure delivered");
System.out.println(checks+" weather regression checks passed");}}
"""
}
with tempfile.TemporaryDirectory(prefix="weather-test-") as directory:
    temp=Path(directory)
    files=[]
    for name, source in SOURCES.items():
        path=temp/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(source,encoding="utf-8")
        files.append(str(path))
    files += [str(BASE/"data/repository/WeatherRepository.java"),str(BASE/"utils/Resource.java")]
    subprocess.run(["javac","-encoding","UTF-8","-d",str(temp/"classes"),*files],check=True)
    subprocess.run(["java","-cp",str(temp/"classes"),"WeatherRegression"],check=True)
