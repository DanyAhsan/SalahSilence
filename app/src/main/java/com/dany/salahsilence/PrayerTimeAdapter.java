package com.dany.salahsilence;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.List;
public class PrayerTimeAdapter extends RecyclerView.Adapter<PrayerTimeAdapter.PrayerTimeViewHolder> {

    private List<PrayerTime> prayerTimes;
    private OnItemClickListener listener;
    private Context context;

    private int getIconResource(String prayerName) {
        switch (prayerName.toLowerCase()) {
            case "fajr":
                return R.drawable.ic_fajr;
            case "zuhr":
                return R.drawable.ic_zuhr;
            case "asr":
                return R.drawable.ic_asr;
            case "maghrib":
                return R.drawable.ic_maghrib;
            case "isha":
                return R.drawable.ic_isha;
            default:
                return 0;
        }
    }

    public interface OnItemClickListener {
        void onSetStartTimeClick(PrayerTime prayerTime);
        void onSetEndTimeClick(PrayerTime prayerTime);
        void onEnableSwitchChange(PrayerTime prayerTime, boolean isChecked);
    }

    public PrayerTimeAdapter(Context context, List<PrayerTime> prayerTimes, OnItemClickListener listener) {
        this.context = context;
        this.prayerTimes = prayerTimes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PrayerTimeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_prayer_time, parent, false);
        return new PrayerTimeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PrayerTimeViewHolder holder, int position) {
        PrayerTime prayerTime = prayerTimes.get(position);
        holder.bind(prayerTime, listener);
        holder.prayer_icon.setImageResource(getIconResource(prayerTime.getName()));
    }

    @Override
    public int getItemCount() {
        return prayerTimes.size();
    }

    class PrayerTimeViewHolder extends RecyclerView.ViewHolder {

        private TextView prayerName;
        private TextView startTime;
        private TextView endTime;
        private SwitchMaterial enableSwitch;
        private Button setStartTimeButton;
        private Button setEndTimeButton;
        private ImageView prayer_icon;

        public PrayerTimeViewHolder(@NonNull View itemView) {
            super(itemView);
            prayerName = itemView.findViewById(R.id.prayer_name);
            startTime = itemView.findViewById(R.id.start_time);
            endTime = itemView.findViewById(R.id.end_time);
            enableSwitch = itemView.findViewById(R.id.enable_switch);
            setStartTimeButton = itemView.findViewById(R.id.set_start_time);
            setEndTimeButton = itemView.findViewById(R.id.set_end_time);
            prayer_icon = itemView.findViewById(R.id.prayer_icon);
        }

        public void bind(PrayerTime prayerTime, OnItemClickListener listener) {
            prayerName.setText(prayerTime.getName());
            startTime.setText(prayerTime.getStartTime12HourFormat());
            endTime.setText(prayerTime.getEndTime12HourFormat());
            enableSwitch.setChecked(prayerTime.isEnabled());

            setStartTimeButton.setOnClickListener(v -> listener.onSetStartTimeClick(prayerTime));
            setEndTimeButton.setOnClickListener(v -> listener.onSetEndTimeClick(prayerTime));

            enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Update the prayer time's enabled status
                prayerTime.setEnabled(isChecked);

                // Schedule or cancel silent mode
                SilentModeReceiver.scheduleSilentMode(context, prayerTime);

                // Notify listener
                listener.onEnableSwitchChange(prayerTime, isChecked);
            });
        }
    }
}
