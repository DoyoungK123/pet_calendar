package com.example.pet_calender;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.util.Consumer;
import androidx.core.util.Pair;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pet_calender.data.model.PetCalendarEvent;
import com.example.pet_calender.data.repositories.PetCalendarEventRepository;
import com.example.pet_calender.databinding.ActivityMainBinding;
import com.example.pet_calender.databinding.ItemPetCalendarEventBinding;
import com.prolificinteractive.materialcalendarview.CalendarDay;
import com.prolificinteractive.materialcalendarview.MaterialCalendarView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private PetCalendarEventRepository repository;
    private final MutableLiveData<CalendarDay> selectedMonth = new MutableLiveData<>(CalendarDay.today());
    private final PetCalendarEventAdapter adapter = new PetCalendarEventAdapter();
    private Pair<Integer, PetCalendarEvent> selectedItem;
    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                Pair<Integer, PetCalendarEvent> selectedItem = this.selectedItem;
                if (selectedItem == null) return;

                int position = selectedItem.first;
                PetCalendarEvent event = selectedItem.second;

                if (uri == null) {
                    event.setImagePath(null);
                    adapter.notifyItemChanged(position);
                    return;
                }

                File file = new File(getFilesDir(), String.format(Locale.US, "%d.jpg", new Date().getTime()));
                try {
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    OutputStream outputStream = new FileOutputStream(file);
                    byte[] buf = new byte[1024];

                    int len = inputStream.read(buf);
                    while (len > 0) {
                        outputStream.write(buf, 0, len);
                        len = inputStream.read(buf);
                    }

                    inputStream.close();
                    outputStream.close();

                    event.setImagePath(file.getAbsolutePath());
                    adapter.notifyItemChanged(position);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        repository = PetCalendarEventRepository.getInstance(this);

        setContentView(binding.getRoot());

        initUi();
        bindData();
    }

    private void initUi() {
        binding.calendarView.setTitleFormatter(calendarDay ->
                String.format(Locale.KOREA, "%04d년 %d월", calendarDay.getYear(), calendarDay.getMonth()));

        binding.calendarView.setTileHeight((int) (getResources().getDisplayMetrics().density * 40));
        binding.calendarView.setSelectionMode(MaterialCalendarView.SELECTION_MODE_SINGLE);
        binding.calendarView.setDateSelected(CalendarDay.today(), true);

        binding.calendarView.setOnMonthChangedListener((materialCalendarView, calendarDay) -> {
            CalendarDay old = selectedMonth.getValue();
            if (old != null) {
                repository.setEvents(old.getYear(), old.getMonth(), adapter.getCurrentList());
            }

            selectedMonth.setValue(calendarDay);
            hideKeyboard();
        });

        adapter.setOnImageClickListener(value -> {
            selectedItem = value;
            getContent.launch("image/*");
        });

        binding.recyclerView.setAdapter(adapter);
    }

    private void bindData() {
        selectedMonth.observe(this, calendarDay -> {
            List<PetCalendarEvent> events = repository.getEvents(calendarDay.getYear(), calendarDay.getMonth());
            adapter.submitList(events);
        });
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();

        if (view != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    protected void onPause() {
        CalendarDay old = selectedMonth.getValue();
        if (old != null) {
            repository.setEvents(old.getYear(), old.getMonth(), adapter.getCurrentList());
        }

        super.onPause();
    }

    private static class PetCalendarEventAdapter extends ListAdapter<PetCalendarEvent, PetCalendarEventAdapter.PetCalendarEventItemViewHolder> {
        private Consumer<Pair<Integer, PetCalendarEvent>> onImageClickListener;

        protected PetCalendarEventAdapter() {
            super(new DiffUtil.ItemCallback<PetCalendarEvent>() {
                @Override
                public boolean areItemsTheSame(@NonNull PetCalendarEvent lhs, @NonNull PetCalendarEvent rhs) {
                    return lhs.getYear() == rhs.getYear() && lhs.getMonth() == rhs.getMonth() && lhs.getDay() == rhs.getDay();
                }

                @Override
                public boolean areContentsTheSame(@NonNull PetCalendarEvent lhs, @NonNull PetCalendarEvent rhs) {
                    return TextUtils.equals(lhs.getMemo(), rhs.getMemo()) &&
                            TextUtils.equals(lhs.getImagePath(), rhs.getImagePath());
                }
            });
        }

        public void setOnImageClickListener(Consumer<Pair<Integer, PetCalendarEvent>> onImageClickListener) {
            this.onImageClickListener = onImageClickListener;
        }

        @NonNull
        @Override
        public PetCalendarEventItemViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
            LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
            ItemPetCalendarEventBinding binding = ItemPetCalendarEventBinding.inflate(inflater, viewGroup, false);
            return new PetCalendarEventItemViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull PetCalendarEventItemViewHolder holder, int i) {
            CalendarDay today = CalendarDay.today();
            PetCalendarEvent event = getItem(i);
            ItemPetCalendarEventBinding binding = holder.binding;
            boolean isTodayEvent = today.getYear() == event.getYear() && today.getMonth() == event.getMonth() && today.getDay() == event.getDay();

            binding.dayTextView.setText(String.format(Locale.US, "%d일", event.getDay()));
            binding.todayIndicator.setVisibility(isTodayEvent ? View.VISIBLE : View.GONE);

            binding.imageContainer.setOnClickListener(v -> {
                if (onImageClickListener != null) {
                    onImageClickListener.accept(new Pair<>(i, event));
                }
            });

            if (TextUtils.isEmpty(event.getImagePath())) {
                binding.imageView.setImageDrawable(null);
            } else {
                binding.imageView.setImageURI(Uri.parse(event.getImagePath()));
            }

            binding.memoEditText.setText("");
            if (!TextUtils.isEmpty(event.getMemo())) {
                binding.memoEditText.append(event.getMemo());
            }

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    event.setMemo(s.toString());
                }
            };

            if (binding.memoEditText.getTag() instanceof TextWatcher) {
                binding.memoEditText.removeTextChangedListener((TextWatcher) binding.memoEditText.getTag());
            }

            binding.memoEditText.setTag(textWatcher);
            binding.memoEditText.addTextChangedListener(textWatcher);
        }

        static class PetCalendarEventItemViewHolder extends RecyclerView.ViewHolder {
            public final ItemPetCalendarEventBinding binding;

            public PetCalendarEventItemViewHolder(ItemPetCalendarEventBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}