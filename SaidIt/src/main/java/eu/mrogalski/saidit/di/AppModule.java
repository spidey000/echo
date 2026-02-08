package eu.mrogalski.saidit.di;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public AudioConfig provideAudioConfig() {
        return new AudioConfig(48000, 1);
    }

    public static class AudioConfig {
        public final int sampleRate;
        public final int channels;
        public AudioConfig(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }
}
