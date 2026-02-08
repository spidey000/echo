package eu.mrogalski.saidit.ml;

import android.content.Context;
import android.util.Log;

import org.tensorflow.lite.support.audio.TensorAudio;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.audio.classifier.AudioClassifier;
import org.tensorflow.lite.task.audio.classifier.Classifications;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioEventClassifier implements TfLiteClassifier {
    private static final String TAG = "AudioEventClassifier";
    private AudioClassifier classifier;

    @Override
    public void load(Context context, String modelPath, String labelPath) throws IOException {
        try {
            classifier = AudioClassifier.createFromFile(context, modelPath);
        } catch (IOException e) {
            Log.e(TAG, "Failed to create audio classifier.", e);
            throw e;
        }
    }

    @Override
    public List<Recognition> recognize(short[] audioBuffer) {
        List<Recognition> recognitions = new ArrayList<>();
        if (classifier == null) {
            return recognitions;
        }

        TensorAudio tensorAudio = classifier.createInputTensorAudio();
        tensorAudio.load(audioBuffer);
        List<Classifications> output = classifier.classify(tensorAudio);

        for (Classifications classifications : output) {
            for (Category category : classifications.getCategories()) {
                recognitions.add(new Recognition(
                        String.valueOf(category.getIndex()),
                        category.getLabel(),
                        category.getScore()
                ));
            }
        }
        return recognitions;
    }

    @Override
    public void close() {
        if (classifier != null) {
            // Classifier doesn't have a close method in the Task Library
            classifier = null;
        }
    }
}
