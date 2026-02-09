package eu.mrogalski.saidit.di;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava"
})
public final class AppModule_ProvideAudioConfigFactory implements Factory<AppModule.AudioConfig> {
  private final AppModule module;

  public AppModule_ProvideAudioConfigFactory(AppModule module) {
    this.module = module;
  }

  @Override
  public AppModule.AudioConfig get() {
    return provideAudioConfig(module);
  }

  public static AppModule_ProvideAudioConfigFactory create(AppModule module) {
    return new AppModule_ProvideAudioConfigFactory(module);
  }

  public static AppModule.AudioConfig provideAudioConfig(AppModule instance) {
    return Preconditions.checkNotNullFromProvides(instance.provideAudioConfig());
  }
}
