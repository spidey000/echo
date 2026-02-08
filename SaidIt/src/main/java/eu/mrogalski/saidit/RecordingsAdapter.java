package eu.mrogalski.saidit;

import android.content.ContentResolver;
import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RecordingsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final List<Object> items;
    private final Context context;
    private MediaPlayer mediaPlayer;
    private int playingPosition = -1;

    public RecordingsAdapter(Context context, List<RecordingItem> recordings) {
        this.context = context;
        this.items = groupRecordingsByDate(recordings);
    }

    private List<Object> groupRecordingsByDate(List<RecordingItem> recordings) {
        List<Object> groupedList = new ArrayList<>();
        if (recordings.isEmpty()) {
            return groupedList;
        }

        String lastHeader = "";
        for (RecordingItem recording : recordings) {
            String header = getDayHeader(recording.getDate());
            if (!header.equals(lastHeader)) {
                groupedList.add(header);
                lastHeader = header;
            }
            groupedList.add(recording);
        }
        return groupedList;
    }

    private String getDayHeader(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar timeToCheck = Calendar.getInstance();
        timeToCheck.setTimeInMillis(timestamp * 1000);

        if (now.get(Calendar.YEAR) == timeToCheck.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == timeToCheck.get(Calendar.DAY_OF_YEAR)) {
            return "Today";
        } else {
            now.add(Calendar.DAY_OF_YEAR, -1);
            if (now.get(Calendar.YEAR) == timeToCheck.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == timeToCheck.get(Calendar.DAY_OF_YEAR)) {
                return "Yesterday";
            } else {
                return new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(timeToCheck.getTime());
            }
        }
    }

    public void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
            playingPosition = -1;
            notifyDataSetChanged();
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (items.get(position) instanceof String) {
            return TYPE_HEADER;
        }
        return TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_header, parent, false);
            return new HeaderViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_recording, parent, false);
        return new RecordingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == TYPE_HEADER) {
            HeaderViewHolder headerHolder = (HeaderViewHolder) holder;
            headerHolder.bind((String) items.get(position));
        } else {
            RecordingViewHolder itemHolder = (RecordingViewHolder) holder;
            RecordingItem recording = (RecordingItem) items.get(position);
            itemHolder.bind(recording);

            if (position == playingPosition) {
                itemHolder.playButton.setIconResource(R.drawable.ic_pause);
            } else {
                itemHolder.playButton.setIconResource(R.drawable.ic_play_arrow);
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class RecordingViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView infoTextView;
        private final MaterialButton playButton;
        private final MaterialButton deleteButton;

        public RecordingViewHolder(@NonNull View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.recording_name_text);
            infoTextView = itemView.findViewById(R.id.recording_info_text);
            playButton = itemView.findViewById(R.id.play_button);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }

        public void bind(RecordingItem recording) {
            nameTextView.setText(recording.getName());

            Date date = new Date(recording.getDate() * 1000); // MediaStore date is in seconds
            SimpleDateFormat formatter = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
            String dateString = formatter.format(date);

            long durationMillis = recording.getDuration();
            String durationString = String.format(Locale.getDefault(), "%02d:%02d",
                    TimeUnit.MILLISECONDS.toMinutes(durationMillis),
                    TimeUnit.MILLISECONDS.toSeconds(durationMillis) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationMillis))
            );

            infoTextView.setText(String.format("%s | %s", durationString, dateString));

            playButton.setOnClickListener(v -> handlePlayback(recording, getAdapterPosition()));

            deleteButton.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(context)
                        .setTitle("Delete Recording")
                        .setMessage("Are you sure you want to permanently delete this file?")
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton("Delete", (dialog, which) -> {
                            int currentPosition = getAdapterPosition();
                            if (currentPosition != RecyclerView.NO_POSITION) {
                                // Stop playback if the deleted item is the one playing
                                if (playingPosition == currentPosition) {
                                    releasePlayer();
                                }

                                RecordingItem itemToDelete = (RecordingItem) items.get(currentPosition);
                                ContentResolver contentResolver = context.getContentResolver();
                                int deletedRows = contentResolver.delete(itemToDelete.getUri(), null, null);

                                if (deletedRows > 0) {
                                    items.remove(currentPosition);
                                    notifyItemRemoved(currentPosition);
                                    notifyItemRangeChanged(currentPosition, items.size());
                                    // Adjust playing position if an item before it was removed
                                    if (playingPosition > currentPosition) {
                                        playingPosition--;
                                    }

                                    // Check if the header is now orphaned
                                    if (currentPosition > 0 && items.get(currentPosition - 1) instanceof String) {
                                        if (currentPosition == items.size() || items.get(currentPosition) instanceof String) {
                                            items.remove(currentPosition - 1);
                                            notifyItemRemoved(currentPosition - 1);
                                            notifyItemRangeChanged(currentPosition - 1, items.size());
                                            if (playingPosition >= currentPosition) {
                                                playingPosition--;
                                            }
                                        }
                                    }
                                }
                            }
                        })
                        .show();
            });
        }

        private void handlePlayback(RecordingItem recording, int position) {
            if (playingPosition == position) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.pause();
                    playButton.setIconResource(R.drawable.ic_play_arrow);
                } else {
                    mediaPlayer.start();
                    playButton.setIconResource(R.drawable.ic_pause);
                }
            } else {
                if (mediaPlayer != null) {
                    mediaPlayer.release();
                    notifyItemChanged(playingPosition);
                }

                int previousPlayingPosition = playingPosition;
                playingPosition = position;

                if (previousPlayingPosition != -1) {
                    notifyItemChanged(previousPlayingPosition);
                }

                mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(context, recording.getUri());
                    mediaPlayer.prepare();
                    mediaPlayer.setOnCompletionListener(mp -> {
                        playingPosition = -1;
                        notifyItemChanged(position);
                    });
                    mediaPlayer.start();
                    playButton.setIconResource(R.drawable.ic_pause);
                } catch (IOException e) {
                    e.printStackTrace();
                    playingPosition = -1;
                }
            }
        }
    }

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView headerTextView;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTextView = itemView.findViewById(R.id.header_text_view);
        }

        public void bind(String text) {
            headerTextView.setText(text);
        }
    }
}
