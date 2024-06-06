package com.example.pet_calender.data.repositories;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.example.pet_calender.data.model.PetCalendarEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.prolificinteractive.materialcalendarview.CalendarDay;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class PetCalendarEventRepository {
    private static PetCalendarEventRepository instance;

    public static PetCalendarEventRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PetCalendarEventRepository(context);
        }

        return instance;
    }

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private PetCalendarEventRepository(Context context) {
        prefs = context.getSharedPreferences("com.example.pet_calendar.event", Context.MODE_PRIVATE);
    }

    private String getKey(int year, int month) {
        return String.format(Locale.US, "%04d%02d", year, month);
    }

    public List<PetCalendarEvent> getEvents(int year, int month) {
        CalendarDay today = CalendarDay.today();

        String json = prefs.getString(getKey(year, month), "[]");
        Type type = new TypeToken<ArrayList<PetCalendarEvent>>() {
        }.getType();

        ArrayList<PetCalendarEvent> events = gson.fromJson(json, type);

        if (year == today.getYear() && month == today.getMonth()) {
            int day = today.getDay();

            if (events.stream().filter(e -> e.getDay() == day).count() == 0) {
                events.add(0, new PetCalendarEvent(year, month, day));
            }
        }

        return events;
    }

    public void setEvents(int year, int month, List<PetCalendarEvent> events) {
        List<PetCalendarEvent> filteredEvents = events.stream()
                .filter(e -> !TextUtils.isEmpty(e.getMemo()) && !TextUtils.isEmpty(e.getImagePath()))
                .collect(Collectors.toList());

        prefs.edit()
                .putString(getKey(year, month), gson.toJson(filteredEvents))
                .apply();
    }
}
