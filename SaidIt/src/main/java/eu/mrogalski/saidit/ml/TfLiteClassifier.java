package eu.mrogalski.saidit.ml;

import android.content.Context;
import java.io.IOException;
import java.util.List;

public interface TfLiteClassifier {

    class Recognition {
        private final String id;
        private final String title;
        private final Float confidence;

        public Recognition(final String id, final String title, final Float confidence) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }
    }

    void load(Context context, String modelPath, String labelPath) throws IOException;

    List<Recognition> recognize(short[] audioBuffer);

    void close();
}
