package com.echo.data.recording;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class RecordingRepositoryImpl_Factory implements Factory<RecordingRepositoryImpl> {
  @Override
  public RecordingRepositoryImpl get() {
    return newInstance();
  }

  public static RecordingRepositoryImpl_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static RecordingRepositoryImpl newInstance() {
    return new RecordingRepositoryImpl();
  }

  private static final class InstanceHolder {
    private static final RecordingRepositoryImpl_Factory INSTANCE = new RecordingRepositoryImpl_Factory();
  }
}
