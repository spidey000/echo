package eu.mrogalski.saidit.export;

import java.io.File;
import java.io.IOException;

public interface AudioExporter {
    void export(File pcmFile, File outputFile, int sampleRate, int channels, int bitRate) throws IOException;
}
