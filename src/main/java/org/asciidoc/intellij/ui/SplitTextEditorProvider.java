package org.asciidoc.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public abstract class SplitTextEditorProvider implements AsyncFileEditorProvider, DumbAware {

  private static final String FIRST_EDITOR = "first_editor";
  private static final String SECOND_EDITOR = "second_editor";
  private static final String SPLIT_LAYOUT = "split_layout";

  @NotNull
  private final FileEditorProvider myFirstProvider;
  @NotNull
  private final FileEditorProvider mySecondProvider;

  @NotNull
  private final String myEditorTypeId;

  protected SplitTextEditorProvider(@NotNull FileEditorProvider firstProvider, @NotNull FileEditorProvider secondProvider) {
    myFirstProvider = firstProvider;
    mySecondProvider = secondProvider;

    myEditorTypeId = "split-provider[" + myFirstProvider.getEditorTypeId() + ";" + mySecondProvider.getEditorTypeId() + "]";
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return myFirstProvider.accept(project, file) && mySecondProvider.accept(project, file);
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return createEditorAsync(project, file).build();
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return myEditorTypeId;
  }

  @NotNull
  @Override
  public Builder createEditorAsync(@NotNull final Project project, @NotNull final VirtualFile file) {
    final Builder firstBuilder = getBuilderFromEditorProvider(myFirstProvider, project, file);
    final Builder secondBuilder = getBuilderFromEditorProvider(mySecondProvider, project, file);

    return new Builder() {
      @Override
      public @NotNull FileEditor build() {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
          // FileEditorManagerImpl$dumbModeFinished$fileToNewProviders will call this in a background thread
          // EditorImpl wants to be called from EDT only, let's switch to EDT for this.
          // This is a known problem: https://youtrack.jetbrains.com/issue/IDEA-318259 in 2023.2 and will be fixed in 2023.3
          AtomicReference<FileEditor> editor = new AtomicReference<>();
          ApplicationManager.getApplication().invokeAndWait(() -> {
            editor.set(createSplitEditor(firstBuilder.build(), secondBuilder.build()));
          });
          return editor.get();
        } else {
          return createSplitEditor(firstBuilder.build(), secondBuilder.build());
        }
      }
    };
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    Element child = sourceElement.getChild(FIRST_EDITOR);
    FileEditorState firstState = null;
    if (child != null) {
      firstState = myFirstProvider.readState(child, project, file);
    }
    child = sourceElement.getChild(SECOND_EDITOR);
    FileEditorState secondState = null;
    if (child != null) {
      secondState = mySecondProvider.readState(child, project, file);
    }

    final Attribute attribute = sourceElement.getAttribute(SPLIT_LAYOUT);

    final String layoutName;
    if (attribute != null) {
      layoutName = attribute.getValue();
    } else {
      layoutName = null;
    }

    return new SplitFileEditor.MyFileEditorState(layoutName, firstState, secondState);
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
    if (!(state instanceof SplitFileEditor.MyFileEditorState)) {
      return;
    }
    final SplitFileEditor.MyFileEditorState compositeState = (SplitFileEditor.MyFileEditorState) state;

    Element child = new Element(FIRST_EDITOR);
    if (compositeState.getFirstState() != null) {
      myFirstProvider.writeState(compositeState.getFirstState(), project, child);
      targetElement.addContent(child);
    }

    child = new Element(SECOND_EDITOR);
    if (compositeState.getSecondState() != null) {
      mySecondProvider.writeState(compositeState.getSecondState(), project, child);
      targetElement.addContent(child);
    }

    if (compositeState.getSplitLayout() != null) {
      targetElement.setAttribute(SPLIT_LAYOUT, compositeState.getSplitLayout());
    }
  }

  protected abstract FileEditor createSplitEditor(@NotNull FileEditor firstEditor, @NotNull FileEditor secondEditor);

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }

  @NotNull
  private static Builder getBuilderFromEditorProvider(@NotNull final FileEditorProvider provider,
                                                      @NotNull final Project project,
                                                      @NotNull final VirtualFile file) {
    if (provider instanceof AsyncFileEditorProvider) {
      return ((AsyncFileEditorProvider) provider).createEditorAsync(project, file);
    } else {
      return new Builder() {
        @Override
        public FileEditor build() {
          return provider.createEditor(project, file);
        }
      };
    }
  }
}
